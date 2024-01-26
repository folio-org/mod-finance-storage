package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;
import org.folio.service.transactions.cancel.CancelTransactionService;
import org.folio.utils.MoneyUtils;
import org.folio.service.budget.BudgetService;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.Boolean.TRUE;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

public class PaymentCreditService extends AbstractTransactionService implements TransactionManagingStrategy {

  private static final Logger logger = LogManager.getLogger(PaymentCreditService.class);

  private static final String TRANSACTIONS_TABLE = "transaction";

  public static final String SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE =
    "SELECT b.jsonb FROM %s b INNER JOIN (SELECT DISTINCT budgets.id FROM %s budgets INNER JOIN %s transactions "
    + "ON ((budgets.fundId = transactions.fromFundId OR budgets.fundId = transactions.toFundId) AND "
    + "transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = $1 AND "
    + "(transactions.jsonb ->> 'transactionType' = 'Payment' OR transactions.jsonb ->> 'transactionType' = 'Credit')) "
    + "sub ON sub.id = b.id "
    + "FOR UPDATE OF b";

  public static final String SELECT_PERMANENT_TRANSACTIONS = "SELECT DISTINCT ON (permtransactions.id) permtransactions.jsonb FROM %s AS permtransactions INNER JOIN %s AS transactions "
    + "ON transactions.paymentEncumbranceId = permtransactions.id WHERE transactions.sourceInvoiceId = $1 AND (transactions.jsonb ->> 'transactionType' = 'Payment' OR transactions.jsonb ->> 'transactionType' = 'Credit')";
  public static final String TRANSACTION_TYPE = "transactionType";
  public static final String SOURCE_INVOICE_ID = "sourceInvoiceId";

  Predicate<Transaction> hasEncumbrance = txn -> txn.getPaymentEncumbranceId() != null;
  Predicate<Transaction> isPaymentTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.PAYMENT);
  Predicate<Transaction> isCreditTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.CREDIT);

  private final AllOrNothingTransactionService allOrNothingTransactionService;
  private final BudgetService budgetService;
  private final CancelTransactionService cancelTransactionService;

  public PaymentCreditService(AllOrNothingTransactionService allOrNothingTransactionService,
                              TransactionDAO transactionDAO,
                              BudgetService budgetService,
                              CancelTransactionService cancelTransactionService) {
    super(transactionDAO);
    this.allOrNothingTransactionService = allOrNothingTransactionService;
    this.budgetService = budgetService;
    this.cancelTransactionService = cancelTransactionService;
  }

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, DBConn conn) {
    return allOrNothingTransactionService.createTransaction(transaction, conn, this::createTransactions);
  }

  @Override
  public Future<Void> updateTransaction(Transaction transaction, DBConn conn) {
    return allOrNothingTransactionService.updateTransaction(transaction, conn, this::updateTransactions);
  }

  /**
   * Before the temporary transaction can be moved to permanent transaction table, the corresponding fields in encumbrances and
   * budgets for payments/credits must be recalculated. To avoid partial transactions, all payments and credits will be done in a
   * database transaction
   *
   * @param transactions to be processed
   * @param conn - database connection
   */

  public Future<Void> createTransactions(List<Transaction> transactions, DBConn conn) {
    String summaryId = getSummaryId(transactions.get(0));
    return updateEncumbranceTotals(transactions, conn)
      .compose(dbc -> updateBudgetsTotals(transactions, conn))
      .compose(dbc -> transactionDAO.saveTransactionsToPermanentTable(summaryId, conn))
      .compose(integer -> deletePendingPayments(summaryId, conn));
  }

  public Future<Void> updateTransactions(List<Transaction> tmpTransactions, DBConn conn) {
    return getTransactions(tmpTransactions, conn)
      .map(existingTransactions -> tmpTransactions.stream()
        .filter(tr -> TRUE.equals(tr.getInvoiceCancelled()))
        .filter(tr -> existingTransactions.stream().anyMatch(tr2 -> tr2.getId().equals(tr.getId()) &&
          !TRUE.equals(tr2.getInvoiceCancelled())))
        .collect(toList()))
      .compose(transactionsToCancel -> cancelTransactionService.cancelTransactions(transactionsToCancel, conn));
  }

  private Future<List<Transaction>> getTransactions(List<Transaction> tmpTransactions, DBConn conn) {
    List<String> ids = tmpTransactions.stream()
      .map(Transaction::getId)
      .collect(toList());
    return transactionDAO.getTransactions(ids, conn);
  }

  private Future<Void> deletePendingPayments(String summaryId, DBConn conn) {
    CriterionBuilder criterionBuilder = new CriterionBuilder();
    criterionBuilder.withJson(SOURCE_INVOICE_ID, "=", summaryId)
      .withJson(TRANSACTION_TYPE, "=", PENDING_PAYMENT.value());
    return transactionDAO.deleteTransactions(criterionBuilder.build(), conn);
  }

  /**
   * Update the Encumbrance transaction attached to the payment/Credit(from paymentEncumbranceID)
   * in a transaction
   *
   * @param transactions to be processed
   * @param conn - database connection
   */
  private Future<DBConn> updateEncumbranceTotals(List<Transaction> transactions, DBConn conn) {
    boolean noEncumbrances = transactions
      .stream()
      .allMatch(transaction -> StringUtils.isBlank(transaction.getPaymentEncumbranceId()));

    if (noEncumbrances) {
      return Future.succeededFuture(conn);
    }
    String summaryId = getSummaryId(transactions.get(0));
    return getAllEncumbrances(summaryId, conn).map(encumbrances -> encumbrances.stream()
      .collect(toMap(Transaction::getId, identity())))
      .map(encumbrancesMap -> applyPayments(transactions, encumbrancesMap))
      .map(encumbrancesMap -> applyCredits(transactions, encumbrancesMap))
      //update all the re-calculated encumbrances into the Transaction table
      .map(map -> new ArrayList<>(map.values()))
      .compose(trns -> transactionDAO.updatePermanentTransactions(trns, conn))
      .compose(ok -> Future.succeededFuture(conn));
  }

  private Future<Integer> updateBudgetsTotals(List<Transaction> transactions, DBConn conn) {
    String summaryId = getSummaryId(transactions.get(0));
    String sql = getSelectBudgetsQueryForUpdate(conn.getTenantId());
    return budgetService.getBudgets(sql, Tuple.of(UUID.fromString(summaryId)), conn)
      .map(budgets -> budgets.stream().collect(toMap(Budget::getFundId, Function.identity())))
      .map(groupedBudgets -> calculatePaymentBudgetsTotals(transactions, groupedBudgets))
      .map(grpBudgets -> calculateCreditBudgetsTotals(transactions, grpBudgets))
      .compose(grpBudgets -> budgetService.updateBatchBudgets(grpBudgets.values(), conn));
  }

  private Map<String, Budget> calculatePaymentBudgetsTotals(List<Transaction> tempTransactions,
      Map<String, Budget> groupedBudgets) {
    Map<Budget, List<Transaction>> paymentBudgetsGrouped = tempTransactions.stream()
      .filter(isPaymentTransaction)
      .collect(groupingBy(transaction -> groupedBudgets.get(transaction.getFromFundId())));

    if (!paymentBudgetsGrouped.isEmpty()) {
      logger.debug("calculatePaymentBudgetsTotals:: Calculating budget totals for payment transactions");
      paymentBudgetsGrouped.entrySet()
        .forEach(this::updateBudgetPaymentTotals);
    }

    return groupedBudgets;
  }

  private Map<String, Budget> calculateCreditBudgetsTotals(List<Transaction> tempTransactions, Map<String, Budget> groupedBudgets) {
    Map<Budget, List<Transaction>> creditBudgetsGrouped = tempTransactions.stream()
      .filter(isCreditTransaction)
      .collect(groupingBy(transaction -> groupedBudgets.get(transaction.getToFundId())));

    if (!creditBudgetsGrouped.isEmpty()) {
      logger.debug("calculateCreditBudgetsTotals:: Calculating budget totals for credit transactions");
      creditBudgetsGrouped.entrySet()
        .forEach(this::updateBudgetCreditTotals);
    }

    return groupedBudgets;
  }

  private void updateBudgetPaymentTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    CurrencyUnit currency = Monetary.getCurrency(entry.getValue()
      .get(0)
      .getCurrency());
    entry.getValue()
      .forEach(txn -> {
        budget.setExpenditures(MoneyUtils.sumMoney(budget.getExpenditures(), txn.getAmount(), currency));
        double newAwaitingPayment = MoneyUtils.subtractMoney(budget.getAwaitingPayment(), txn.getAmount(), currency);
        budget.setAwaitingPayment(newAwaitingPayment);
        budgetService.updateBudgetMetadata(budget, txn);
        budgetService.clearReadOnlyFields(budget);
      });
  }

  private void updateBudgetCreditTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    CurrencyUnit currency = Monetary.getCurrency(entry.getValue()
      .get(0)
      .getCurrency());
    entry.getValue()
      .forEach(txn -> {
        budget.setExpenditures(MoneyUtils.subtractMoney(budget.getExpenditures(), txn.getAmount(), currency));
        budget.setAwaitingPayment(MoneyUtils.sumMoney(budget.getAwaitingPayment(), txn.getAmount(), currency));
        budgetService.updateBudgetMetadata(budget, txn);
        budgetService.clearReadOnlyFields(budget);
      });
  }

  /**
   * <pre>
   * Encumbrances are recalculated with the credit transaction as below:
   * - expended decreases by credit transaction amount (min 0)
   * </pre>
   */
  private Map<String, Transaction> applyCredits(List<Transaction> tempTxns, Map<String, Transaction> encumbrancesMap) {

    List<Transaction> tempCredits = tempTxns.stream()
      .filter(isCreditTransaction.and(hasEncumbrance))
      .toList();

    if (tempCredits.isEmpty()) {
      return encumbrancesMap;
    }
    CurrencyUnit currency = Monetary.getCurrency(tempCredits.get(0)
      .getCurrency());
    tempCredits.forEach(creditTxn -> {
      Transaction encumbranceTxn = encumbrancesMap.get(creditTxn.getPaymentEncumbranceId());

      double newExpended = MoneyUtils.subtractMoneyNonNegative(encumbranceTxn.getEncumbrance()
              .getAmountExpended(), creditTxn.getAmount(), currency);
      double newAwaitingPayment = MoneyUtils.sumMoney(encumbranceTxn.getEncumbrance()
              .getAmountAwaitingPayment(), creditTxn.getAmount(), currency);
      encumbranceTxn.getEncumbrance()
        .withAmountExpended(newExpended)
        .withAmountAwaitingPayment(newAwaitingPayment);

    });

    return encumbrancesMap;
  }

  /**
   * <pre>
   * Encumbrances are recalculated with the payment transaction as below:
   * - expended increases by payment transaction amount
   * </pre>
   */
  private Map<String, Transaction> applyPayments(List<Transaction> tempTxns, Map<String, Transaction> encumbrancesMap) {
    List<Transaction> tempPayments = tempTxns.stream()
      .filter(isPaymentTransaction.and(hasEncumbrance))
      .toList();
    if (tempPayments.isEmpty()) {
      return encumbrancesMap;
    }

    CurrencyUnit currency = Monetary.getCurrency(tempPayments.get(0)
      .getCurrency());
    tempPayments.forEach(pymtTxn -> {
      Transaction encumbranceTxn = encumbrancesMap.get(pymtTxn.getPaymentEncumbranceId());
      double newExpended = MoneyUtils.sumMoney(encumbranceTxn.getEncumbrance()
              .getAmountExpended(), pymtTxn.getAmount(), currency);
      double newAwaitingPayment = MoneyUtils.subtractMoney(encumbranceTxn.getEncumbrance()
              .getAmountAwaitingPayment(), pymtTxn.getAmount(), currency);

      encumbranceTxn.getEncumbrance()
        .withAmountExpended(newExpended)
        .withAmountAwaitingPayment(newAwaitingPayment);

    });

    return encumbrancesMap;
  }

  private Future<List<Transaction>> getAllEncumbrances(String summaryId, DBConn conn) {
    logger.debug("getAllEncumbrances:: Trying to get all encumbrances by summary id {}", summaryId);
    String sql = buildGetPermanentEncumbrancesQuery(conn.getTenantId());
    return conn.execute(sql, Tuple.of(UUID.fromString(summaryId)))
      .map(rowSet -> {
        List<Transaction> encumbrances = new ArrayList<>();
        rowSet.spliterator().forEachRemaining(row -> encumbrances.add(row.get(JsonObject.class, 0).mapTo(Transaction.class)));
        return encumbrances;
      })
      .onSuccess(encumbrances -> logger.info("Successfully retrieved {} encumbrances by summary id {}",
        encumbrances.size(), summaryId))
      .onFailure(e -> logger.error("getAllEncumbrances:: Getting all encumbrances by summary id {} failed", summaryId, e));
  }

  private String buildGetPermanentEncumbrancesQuery(String tenantId) {
    return String.format(SELECT_PERMANENT_TRANSACTIONS, getFullTableName(tenantId, TRANSACTIONS_TABLE),
        getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
  }

  private String getSelectBudgetsQueryForUpdate(String tenantId){
    String budgetTableName = getFullTableName(tenantId, BUDGET_TABLE);
    String transactionTableName = getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS);
    return String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);
  }

  public Transaction.TransactionType getStrategyName() {
    return PAYMENT;
  }


  public String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }
}
