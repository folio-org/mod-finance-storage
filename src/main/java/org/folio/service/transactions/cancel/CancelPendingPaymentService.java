package org.folio.service.transactions.cancel;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.utils.MoneyUtils.sumMoney;

public class CancelPendingPaymentService implements CancelTransactionService {

  private final TransactionDAO transactionsDAO;
  private final BudgetService budgetService;

  public CancelPendingPaymentService(TransactionDAO transactionsDAO, BudgetService budgetService) {
    this.transactionsDAO = transactionsDAO;
    this.budgetService = budgetService;
  }

  @Override
  public Future<Void> cancelTransactions(List<Transaction> tmpTransactions, DBClient client){

    return getTransactions(tmpTransactions, client)
      .map(transactionsFromDB -> getVoidedTransactions(tmpTransactions, transactionsFromDB))
      .compose(transactions -> cancelPendingPayments(transactions, client))
      .compose(transactions -> transactionsDAO.updatePermanentTransactions(tmpTransactions, client));
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
      .filter(transaction -> Objects.nonNull(transaction.getInvoiceCancelled()))
      .filter(transaction -> !transaction.getInvoiceCancelled() && idIsCancelledMap.get(transaction.getId()))
      .collect(Collectors.toList());
  }

  private Future<List<Transaction>> cancelPendingPayments(List<Transaction> transactions, DBClient client) {
    String summaryId = getSummaryId(transactions.get(0));

    return budgetService.getBudgets(getSelectBudgetsQuery(client.getTenantId()), Tuple.of(UUID.fromString(summaryId)), client)
      .map(budgets -> cancelBudgetsTotalsPendingPayments(transactions, budgets))
        .compose(newBudgets -> budgetService.updateBatchBudgets(newBudgets, client))
      .map(transactions);
  }

  private List<Budget> cancelBudgetsTotalsPendingPayments(List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<Budget, List<Transaction>> tempGrouped = groupTransactionsByBudget(tempTransactions, budgets);
    return tempGrouped.entrySet().stream()
      .map(this::cancelBudgetTotalsPendingPayments)
      .collect(toList());
  }

  private Map<Budget, List<Transaction>> groupTransactionsByBudget(List<Transaction> existingTransactions, List<Budget> budgets) {
    Map<String, List<Transaction>> groupedTransactions = existingTransactions.stream().collect(groupingBy(Transaction::getFromFundId));

    return budgets.stream()
      .collect(toMap(identity(), budget ->  groupedTransactions.getOrDefault(budget.getFundId(), Collections.emptyList())));

  }

  private Budget cancelBudgetTotalsPendingPayments(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = JsonObject.mapFrom(entry.getKey()).mapTo(Budget.class);
    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {
          double newAwaitingPayment = sumMoney(budget.getAwaitingPayment(), tmpTransaction.getAmount(), currency);
          budget.setAwaitingPayment(newAwaitingPayment);
          budgetService.updateBudgetMetadata(budget, tmpTransaction);
          budgetService.clearReadOnlyFields(budget);
        });
    }
    return budget;
  }

  private String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  private String getSelectBudgetsQuery(String tenantId) {
    return String.format(SELECT_BUDGETS_BY_INVOICE_ID, getFullTableName(tenantId, BUDGET_TABLE), getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
  }
}
