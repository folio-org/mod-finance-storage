package org.folio.service.transactions;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.persist.HelperUtils.buildNullValidationError;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

import org.apache.commons.lang3.StringUtils;
import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.MoneyUtils;
import org.folio.service.budget.BudgetService;
import org.folio.service.calculation.CalculationService;
import org.folio.service.fund.FundService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledgerfy.LedgerFiscalYearService;
import org.folio.service.summary.TransactionSummaryService;
import org.javamoney.moneta.Money;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.sqlclient.Tuple;

public class PaymentCreditAllOrNothingService extends BaseAllOrNothingTransactionService<InvoiceTransactionSummary> {

  private static final String TRANSACTIONS_TABLE = "transaction";

  public static final String SELECT_BUDGETS_BY_INVOICE_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON ((budgets.fundId = transactions.fromFundId OR budgets.fundId = transactions.toFundId) AND transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = $1 AND (transactions.jsonb ->> 'transactionType' = 'Payment' OR transactions.jsonb ->> 'transactionType' = 'Credit')";

  public static final String SELECT_PERMANENT_TRANSACTIONS = "SELECT DISTINCT ON (permtransactions.id) permtransactions.jsonb FROM %s AS permtransactions INNER JOIN %s AS transactions "
    + "ON transactions.paymentEncumbranceId = permtransactions.id WHERE transactions.sourceInvoiceId = $1 AND (transactions.jsonb ->> 'transactionType' = 'Payment' OR transactions.jsonb ->> 'transactionType' = 'Credit')";
  public static final String TRANSACTION_TYPE = "transactionType";
  public static final String SOURCE_INVOICE_ID = "sourceInvoiceId";

  Predicate<Transaction> hasEncumbrance = txn -> txn.getPaymentEncumbranceId() != null;
  Predicate<Transaction> isPaymentTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.PAYMENT);
  Predicate<Transaction> isCreditTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.CREDIT);

  public PaymentCreditAllOrNothingService(BudgetService budgetService,
                                           TemporaryTransactionDAO temporaryTransactionDAO,
                                           CalculationService calculationService,
                                           LedgerFiscalYearService ledgerFiscalYearService,
                                           FundService fundService,
                                           TransactionSummaryService<InvoiceTransactionSummary> transactionSummaryService,
                                           TransactionDAO transactionsDAO,
                                           LedgerService ledgerService) {
    super(budgetService, temporaryTransactionDAO, calculationService, ledgerFiscalYearService, fundService, transactionSummaryService, transactionsDAO, ledgerService);
  }



  /**
   * Before the temporary transaction can be moved to permanent transaction table, the corresponding fields in encumbrances and
   * budgets for payments/credits must be recalculated. To avoid partial transactions, all payments and credits will be done in a
   * database transaction
   *
   * @param transactions to be processed
   * @param client
   */
  @Override
  public Future<Void> processTemporaryToPermanentTransactions(List<Transaction> transactions, DBClient client) {
    String summaryId = getSummaryId(transactions.get(0));
    return updateEncumbranceTotals(transactions, client)
      .compose(dbc -> updateBudgetsTotals(transactions, client))
      .compose(dbc -> transactionsDAO.saveTransactionsToPermanentTable(summaryId, client))
      .compose(integer -> deletePendingPayments(summaryId, client));
  }

  private Future<Void> deletePendingPayments(String summaryId, DBClient client) {
    CriterionBuilder criterionBuilder = new CriterionBuilder();
    criterionBuilder.withJson(SOURCE_INVOICE_ID, "=", summaryId)
      .withJson(TRANSACTION_TYPE, "=", PENDING_PAYMENT.value());
    return transactionsDAO.deleteTransactions(criterionBuilder.build(), client);
  }

  /**
   * Update the Encumbrance transaction attached to the payment/Credit(from paymentEncumbranceID)
   * in a transaction
   *
   * @param transactions
   * @param client : the list of payments and credits
   */
  private Future<DBClient> updateEncumbranceTotals(List<Transaction> transactions, DBClient client) {
    boolean noEncumbrances = transactions
      .stream()
      .allMatch(transaction -> StringUtils.isBlank(transaction.getPaymentEncumbranceId()));

    if (noEncumbrances) {
      return Future.succeededFuture(client);
    }
    String summaryId = getSummaryId(transactions.get(0));
    return getAllEncumbrances(summaryId, client).map(encumbrances -> encumbrances.stream()
      .collect(toMap(Transaction::getId, identity())))
      .map(encumbrancesMap -> applyPayments(transactions, encumbrancesMap))
      .map(encumbrancesMap -> applyCredits(transactions, encumbrancesMap))
      //update all the re-calculated encumbrances into the Transaction table
      .map(map -> map.values()
        .stream()
        .collect(Collectors.toList()))
      .compose(trns -> transactionsDAO.updatePermanentTransactions(trns, client))
      .compose(ok -> Future.succeededFuture(client));

  }

  private Future<Integer> updateBudgetsTotals(List<Transaction> transactions, DBClient client) {
    String summaryId = getSummaryId(transactions.get(0));
    String sql = getSelectBudgetsQuery(client.getTenantId());
    return budgetService.getBudgets(sql, Tuple.of(UUID.fromString(summaryId)), client)
      .map(budgets -> budgets.stream().collect(toMap(Budget::getFundId, Function.identity())))
      .map(groupedBudgets -> calculatePaymentBudgetsTotals(transactions, groupedBudgets))
      .map(grpBudgets -> calculateCreditBudgetsTotals(transactions, grpBudgets))
      .compose(grpBudgets -> budgetService.updateBatchBudgets(grpBudgets.values(), client));
  }

  private Map<String, Budget> calculatePaymentBudgetsTotals(List<Transaction> tempTransactions,
      Map<String, Budget> groupedBudgets) {
    Map<Budget, List<Transaction>> paymentBudgetsGrouped = tempTransactions.stream()
      .filter(isPaymentTransaction)
      .collect(groupingBy(transaction -> groupedBudgets.get(transaction.getFromFundId())));

    if (!paymentBudgetsGrouped.isEmpty()) {
      log.debug("Calculating budget totals for payment transactions");
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
      log.debug("Calculating budget totals for credit transactions");
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
      .collect(Collectors.toList());

    if (tempCredits.isEmpty()) {
      return encumbrancesMap;
    }
    CurrencyUnit currency = Monetary.getCurrency(tempCredits.get(0)
      .getCurrency());
    tempCredits.forEach(creditTxn -> {
      Transaction encumbranceTxn = encumbrancesMap.get(creditTxn.getPaymentEncumbranceId());

      double newExpended = MoneyUtils.subtractMoneyNonNegative(encumbranceTxn.getEncumbrance()
        .getAmountExpended(), creditTxn.getAmount(), currency);
      encumbranceTxn.getEncumbrance()
        .setAmountExpended(newExpended);

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
      .collect(Collectors.toList());
    if (tempPayments.isEmpty()) {
      return encumbrancesMap;
    }

    CurrencyUnit currency = Monetary.getCurrency(tempPayments.get(0)
      .getCurrency());
    tempPayments.forEach(pymtTxn -> {
      Transaction encumbranceTxn = encumbrancesMap.get(pymtTxn.getPaymentEncumbranceId());
      double newExpended = MoneyUtils.sumMoney(encumbranceTxn.getEncumbrance()
        .getAmountExpended(), pymtTxn.getAmount(), currency);

      encumbranceTxn.getEncumbrance()
        .setAmountExpended(newExpended);

    });

    return encumbrancesMap;
  }

  private Future<List<Transaction>> getAllEncumbrances(String summaryId, DBClient client) {
    Promise<List<Transaction>> promise = Promise.promise();
    String sql = buildGetPermanentEncumbrancesQuery(client.getTenantId());
    client.getPgClient()
      .select(client.getConnection(), sql, Tuple.of(UUID.fromString(summaryId)), reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Transaction> encumbrances = new ArrayList<>();
          reply.result().spliterator().forEachRemaining(row -> encumbrances.add(row.get(JsonObject.class, 0).mapTo(Transaction.class)));
          promise.complete(encumbrances);
        }
      });
    return promise.future();
  }

  private String buildGetPermanentEncumbrancesQuery(String tenantId) {
    return String.format(SELECT_PERMANENT_TRANSACTIONS, getFullTableName(tenantId, TRANSACTIONS_TABLE),
        getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
  }

  @Override
  Void handleValidationError(Transaction transaction) {

    List<Error> errors = new ArrayList<>();

    if (isCreditTransaction.test(transaction)) {
      errors.addAll(buildNullValidationError(transaction.getToFundId(), TO_FUND_ID));
    } else {
      errors.addAll(buildNullValidationError(transaction.getFromFundId(), FROM_FUND_ID));
    }
    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
    return null;
  }

  @Override
  protected String getSelectBudgetsQuery(String tenantId){
    return getSelectBudgetsQuery(SELECT_BUDGETS_BY_INVOICE_ID, tenantId, TEMPORARY_INVOICE_TRANSACTIONS);
  }

  @Override
  String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  @Override
  protected boolean isTransactionOverspendRestricted(Ledger ledger, Budget budget) {
    return ledger.getRestrictExpenditures()
      && budget.getAllowableExpenditure() != null;
  }

  /**
   * Calculates remaining amount for payment [remaining amount] = (allocated * allowableExpenditure) - (allocated - (unavailable +
   * available)) - (expenditure + encumbered + awaitingPayment - relatedAwaitingPayment)
   *
   * @param budget             processed budget
   * @param currency
   * @param relatedTransaction
   * @return remaining amount for payment
   */
  @Override
  protected Money getBudgetRemainingAmount(Budget budget, String currency, Transaction relatedTransaction) {
    Money allocated = Money.of(budget.getAllocated(), currency);
    // get allowableExpenditure from percentage value
    double allowableExpenditure = Money.of(budget.getAllowableExpenditure(), currency).divide(100d).getNumber().doubleValue();
    Money unavailable = Money.of(budget.getUnavailable(), currency);
    Money available = Money.of(budget.getAvailable(), currency);
    Money expenditure = Money.of(budget.getExpenditures(), currency);
    Money relatedAwaitingPayment = relatedTransaction == null ? Money.of(0d, currency) : Money.of(relatedTransaction.getAmount(), currency);
    Money awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);
    Money encumbered = Money.of(budget.getEncumbered(), currency);
    Money netTransfers = Money.of(budget.getNetTransfers(), currency);

    Money result = allocated.add(netTransfers).multiply(allowableExpenditure);
    result = result.subtract(allocated.subtract(unavailable.add(available)));
    result = result.subtract(expenditure.add(encumbered).add(awaitingPayment).subtract(relatedAwaitingPayment));

    return result;
  }

  @Override
  protected Future<Transaction> getRelatedTransaction(Transaction transaction, DBClient dbClient) {

    CriterionBuilder criterionBuilder;
    if (transaction.getSourceInvoiceLineId() != null) {
      criterionBuilder = new CriterionBuilder()
        .withJson("fromFundId","=", transaction.getFromFundId())
        .withJson("sourceInvoiceId","=", transaction.getSourceInvoiceId())
        .withJson("sourceInvoiceLineId","=", transaction.getSourceInvoiceLineId())
        .withJson("transactionType","=", PENDING_PAYMENT.value())
        .withOperation("AND");
    } else {
      criterionBuilder = new CriterionBuilder()
        .withJson("fromFundId","=", transaction.getFromFundId())
        .withJson("sourceInvoiceId","=", transaction.getSourceInvoiceId())
        .withJson("sourceInvoiceLineId","IS NULL", null)
        .withJson("transactionType","=", PENDING_PAYMENT.value())
        .withOperation("AND");
    }

    return transactionsDAO.getTransactions(criterionBuilder.build(), dbClient)
      .map(transactions -> transactions.isEmpty() ? null : transactions.get(0));
  }
}
