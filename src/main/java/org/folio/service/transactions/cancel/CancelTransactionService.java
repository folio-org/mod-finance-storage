package org.folio.service.transactions.cancel;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;
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

  private static final String SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE =
    "SELECT b.jsonb FROM %s b INNER JOIN (SELECT DISTINCT budgets.id FROM %s budgets INNER JOIN %s transactions "
      + "ON ((budgets.fundId = transactions.fromFundId OR budgets.fundId = transactions.toFundId) AND "
      + "transactions.fiscalYearId = budgets.fiscalYearId) "
      + "WHERE transactions.sourceInvoiceId = $1 AND "
      + "transactions.jsonb ->> 'transactionType' IN ('Payment', 'Credit', 'Pending payment')) "
      + "sub ON sub.id = b.id "
      + "FOR UPDATE OF b";

  private final BudgetService budgetService;
  private final TransactionDAO paymentCreditDAO;
  private final TransactionDAO encumbranceDAO;

  public CancelTransactionService(BudgetService budgetService, TransactionDAO paymentCreditDAO, TransactionDAO encumbranceDAO) {
    this.budgetService = budgetService;
    this.paymentCreditDAO = paymentCreditDAO;
    this.encumbranceDAO = encumbranceDAO;
  }

  /**
   * Updates given transactions, related encumbrances and related budgets to cancel the transactions.
   * All transactions are supposed to be either pending payment or payment/credit.
   */
  public Future<Void> cancelTransactions(List<Transaction> transactions, DBConn conn) {
    String summaryId = getSummaryId(transactions.get(0));
    return updateRelatedEncumbrances(transactions, conn)
      .compose(v -> budgetService.getBudgets(getSelectBudgetsQuery(conn.getTenantId()),
        Tuple.of(UUID.fromString(summaryId)), conn))
      .map(budgets -> budgetsMoneyBack(transactions, budgets))
      .compose(newBudgets -> budgetService.updateBatchBudgets(newBudgets, conn))
      .compose(n -> paymentCreditDAO.updatePermanentTransactions(transactions, conn));
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
    String budgetTableName = getFullTableName(tenantId, BUDGET_TABLE);
    String transactionTableName = getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS);
    return String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);
  }

  private Future<Void> updateRelatedEncumbrances(List<Transaction> transactions, DBConn conn) {
    Map<String, List<Transaction>> encumbranceIdToTransactions = transactions.stream()
      .filter(tr -> getEncumbranceId(tr).isPresent())
      .collect(groupingBy(tr -> getEncumbranceId(tr).orElse(null)));
    if (encumbranceIdToTransactions.isEmpty())
      return Future.succeededFuture();
    List<String> encumbranceIds = new ArrayList<>(encumbranceIdToTransactions.keySet());
    return encumbranceDAO.getTransactions(encumbranceIds, conn)
      .map(tr -> updateEncumbrances(tr, encumbranceIdToTransactions))
      .compose(encumbrances -> encumbranceDAO.updatePermanentTransactions(encumbrances, conn));
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
