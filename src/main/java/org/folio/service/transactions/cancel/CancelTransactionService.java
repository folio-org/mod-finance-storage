package org.folio.service.transactions.cancel;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNullElse;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

public abstract class CancelTransactionService {

  private final BudgetService budgetService;
  private final TransactionDAO paymentCreditDAO;
  private final TransactionDAO encumbranceDAO;

  public CancelTransactionService(BudgetService budgetService, TransactionDAO paymentCreditDAO, TransactionDAO encumbranceDAO) {
    this.budgetService = budgetService;
    this.paymentCreditDAO = paymentCreditDAO;
    this.encumbranceDAO = encumbranceDAO;
  }

  String SELECT_BUDGETS_BY_INVOICE_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON ((budgets.fundId = transactions.fromFundId OR budgets.fundId = transactions.toFundId) AND transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = $1 AND transactions.jsonb ->> 'transactionType' IN ('Payment', 'Credit', 'Pending payment')";

  /**
   * Updates given transactions, related encumbrances and related budgets to cancel the transactions.
   * All transactions are supposed to be either pending payment or payment/credit.
   */
  public Future<Void> cancelTransactions(List<Transaction> transactions, DBClient client) {
    String summaryId = getSummaryId(transactions.get(0));
    return updateRelatedEncumbrances(transactions, client)
      .compose(v -> budgetService.getBudgets(getSelectBudgetsQuery(client.getTenantId()),
        Tuple.of(UUID.fromString(summaryId)), client))
      .map(budgets -> budgetsMoneyBack(transactions, budgets))
      .compose(newBudgets -> budgetService.updateBatchBudgets(newBudgets, client))
      .compose(n -> paymentCreditDAO.updatePermanentTransactions(transactions, client));
  }

  private List<Budget> budgetsMoneyBack(List<Transaction> transactions, List<Budget> budgets) {
    Map<Budget, List<Transaction>> budgetToTransactions = groupTransactionsByBudget(transactions, budgets);
    return budgetToTransactions.entrySet().stream()
      .map(entry -> budgetMoneyBack(entry.getKey(), entry.getValue()))
      .collect(toList());
  }

  private Map<Budget, List<Transaction>> groupTransactionsByBudget(List<Transaction> transactions, List<Budget> budgets) {
    Map<String, List<Transaction>> fundIdToTransactions = transactions.stream()
      .collect(groupingBy(tr -> requireNonNullElse(tr.getFromFundId(), tr.getToFundId())));
    return budgets.stream()
      .filter(budget -> fundIdToTransactions.get(budget.getFundId()) != null)
      .collect(toMap(identity(), budget -> fundIdToTransactions.get(budget.getFundId())));
  }

  private String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  private String getSelectBudgetsQuery(String tenantId) {
    return String.format(SELECT_BUDGETS_BY_INVOICE_ID, getFullTableName(tenantId, BUDGET_TABLE), getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
  }

  private Future<Void> updateRelatedEncumbrances(List<Transaction> transactions, DBClient client) {
    Map<String, List<Transaction>> encumbranceIdToTransactions = transactions.stream()
      .filter(tr -> getEncumbranceId(tr).isPresent())
      .collect(groupingBy(tr -> getEncumbranceId(tr).orElse(null)));
    if (encumbranceIdToTransactions.isEmpty())
      return Future.succeededFuture();
    List<String> encumbranceIds = new ArrayList<>(encumbranceIdToTransactions.keySet());
    return encumbranceDAO.getTransactions(encumbranceIds, client)
      .map(tr -> updateEncumbrances(tr, encumbranceIdToTransactions))
      .compose(encumbrances -> encumbranceDAO.updatePermanentTransactions(encumbrances, client));
  }

  private List<Transaction> updateEncumbrances(List<Transaction> encumbrances,
      Map<String, List<Transaction>> encumbranceIdToTransactions) {
    encumbrances.forEach(enc -> cancelEncumbrance(enc, encumbranceIdToTransactions.get(enc.getId())));
    return encumbrances;
  }

  protected abstract Budget budgetMoneyBack(Budget budget, List<Transaction> transactions);

  protected abstract Optional<String> getEncumbranceId(Transaction pendingPayment);

  protected abstract void cancelEncumbrance(Transaction encumbrance, List<Transaction> transactions);
}
