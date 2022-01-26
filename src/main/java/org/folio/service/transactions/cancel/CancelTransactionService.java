package org.folio.service.transactions.cancel;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

public abstract class CancelTransactionService {

  private final TransactionDAO transactionsDAO;
  protected final BudgetService budgetService;

  public CancelTransactionService(TransactionDAO transactionsDAO, BudgetService budgetService) {
    this.transactionsDAO = transactionsDAO;
    this.budgetService = budgetService;
  }

  String SELECT_BUDGETS_BY_INVOICE_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON ((budgets.fundId = transactions.fromFundId OR budgets.fundId = transactions.toFundId) AND transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = $1 AND (transactions.jsonb ->> 'transactionType' = 'Payment' OR transactions.jsonb ->> 'transactionType' = 'Credit')";

  public Future<Void> cancelTransactions(List<Transaction> tmpTransactions, DBClient client) {
    return getTransactions(tmpTransactions, client)
      .map(transactions -> getVoidedTransactions(tmpTransactions, transactions))
      .compose(transactions -> cancel(transactions, client))
      .compose(transactions -> transactionsDAO.updatePermanentTransactions(transactions, client));
  }

  private Future<List<Transaction>> getTransactions(List<Transaction> tmpTransactions, DBClient client) {
    List<String> ids = tmpTransactions.stream()
      .map(Transaction::getId)
      .collect(toList());

    return transactionsDAO.getTransactions(ids, client);
  }

  private List<Transaction> getVoidedTransactions(List<Transaction> tmpTransactions, List<Transaction> transactionsFromDB) {
    Map<String, Boolean> idIsCancelledMap = tmpTransactions.stream()
      .filter(transaction -> Objects.nonNull(transaction.getInvoiceCancelled()))
      .collect(toMap(Transaction::getId, Transaction::getInvoiceCancelled));
    return transactionsFromDB.stream()
      .filter(transaction -> !Boolean.TRUE.equals(transaction.getInvoiceCancelled()) && idIsCancelledMap.get(transaction.getId()))
      .collect(Collectors.toList());
  }

  private Future<List<Transaction>> cancel(List<Transaction> transactions, DBClient client) {
    String summaryId = getSummaryId(transactions.get(0));

    return budgetService.getBudgets(getSelectBudgetsQuery(client.getTenantId()), Tuple.of(UUID.fromString(summaryId)), client)
      .map(budgets -> budgetsMoneyBack(transactions, budgets))
      .compose(newBudgets -> budgetService.updateBatchBudgets(newBudgets, client))
      .map(transactions);
  }

  private List<Budget> budgetsMoneyBack(List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<Budget, List<Transaction>> tempGrouped = groupTransactionsByBudget(tempTransactions, budgets);
    return tempGrouped.entrySet().stream()
      .map(this::budgetMoneyBack)
      .collect(toList());
  }

  private Map<Budget, List<Transaction>> groupTransactionsByBudget(List<Transaction> existingTransactions, List<Budget> budgets) {
    Map<String, List<Transaction>> groupedTransactions = new HashMap<>();

    existingTransactions.forEach(transaction -> {
      if (transaction.getFromFundId() != null) {
        groupedTransactions.put(transaction.getFromFundId(), existingTransactions);
      } else {
        groupedTransactions.put(transaction.getToFundId(), existingTransactions);
      }
    });

    return budgets.stream()
      .collect(toMap(identity(), budget ->  groupedTransactions.getOrDefault(budget.getFundId(), Collections.emptyList())));
  }

  abstract Budget budgetMoneyBack(Map.Entry<Budget, List<Transaction>> entry);

  private String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  private String getSelectBudgetsQuery(String tenantId) {
    return String.format(SELECT_BUDGETS_BY_INVOICE_ID, getFullTableName(tenantId, BUDGET_TABLE), getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
  }
}
