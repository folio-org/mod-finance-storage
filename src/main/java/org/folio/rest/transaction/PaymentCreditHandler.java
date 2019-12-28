package org.folio.rest.transaction;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.TransactionSummaryAPI.INVOICE_TRANSACTION_SUMMARIES;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.handleFailure;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.MoneyUtils;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

public class PaymentCreditHandler extends AllOrNothingHandler {

  private static final String TEMPORARY_INVOICE_TRANSACTIONS = "temporary_invoice_transactions";
  private static final String TRANSACTIONS_TABLE = "transaction";

  Predicate<Transaction> hasEncumbrance = txn -> txn.getPaymentEncumbranceId() != null;
  Predicate<Transaction> isPaymentTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.PAYMENT);
  Predicate<Transaction> isCreditTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.CREDIT);

  public PaymentCreditHandler(Map<String, String> okapiHeaders, Context ctx, Handler<AsyncResult<Response>> asyncResultHandler) {
    super(TEMPORARY_INVOICE_TRANSACTIONS, INVOICE_TRANSACTION_SUMMARIES, okapiHeaders, ctx, asyncResultHandler);
  }

  @Override
  String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  @Override
  Criterion getTransactionBySummaryIdCriterion(String value) {
    return getCriterionByFieldNameAndValueNotJsonb("sourceInvoiceId", "=", value);
  }

  @Override
  public void createTransaction(Transaction transaction) {
    processAllOrNothing(transaction, this::processAllPaymentsCredits).setHandler(result -> {
      if (result.failed()) {
        HttpStatusException cause = (HttpStatusException) result.cause();
        if (cause.getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
          getAsyncResultHandler().handle(Future.succeededFuture(
              FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.respond400WithTextPlain(cause.getPayload())));
        } else {
          getAsyncResultHandler().handle(Future.succeededFuture(FinanceStorageTransactions.PostFinanceStorageTransactionsResponse
            .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
        }
      } else {
        log.debug("Preparing response to client");
        getAsyncResultHandler().handle(
            Future.succeededFuture(FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.respond201WithApplicationJson(
                transaction, FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.headersFor201()
                  .withLocation(TRANSACTION_LOCATION_PREFIX + transaction.getId()))));
      }
    });

  }

  /**
   * To avoid partial transactions, all payments and credits must be done in a database transaction
   * @param tx
   */
  public Future<Tx<List<Transaction>>> processAllPaymentsCredits(Tx<List<Transaction>> tx) {
    return updateEncumbranceTotals(tx).compose(this::updateBudgetsTotals)
      .compose(this::createPermanentTransactions);

  }

  private Future<Tx<List<Transaction>>> updateBudgetsTotals(Tx<List<Transaction>> tx) {
    return getBudgets(tx).map(budgets -> budgets.stream()
      .collect(toMap(Budget::getFundId, Function.identity())))
      .map(groupedbudgets -> calculatePaymentBudgetsTotals(tx.getEntity(), groupedbudgets))
      .map(grpBudgets -> calculateCreditBudgetsTotals(tx.getEntity(), grpBudgets))
      .map(grpBudgets -> calculateNonEncumbranceBudgetTotals(tx.getEntity(), grpBudgets))
      .compose(budgets -> updateBudgets(tx, budgets.values()
        .stream()
        .collect(Collectors.toList())));
  }

  private Map<String, Budget> calculatePaymentBudgetsTotals(List<Transaction> tempTransactions,
      Map<String, Budget> groupedBudgets) {
    Map<Budget, List<Transaction>> paymentBudgetsGrouped = tempTransactions.stream()
      .filter(isPaymentTransaction.and(hasEncumbrance))
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
      .filter(isCreditTransaction.and(hasEncumbrance))
      .collect(groupingBy(transaction -> groupedBudgets.get(transaction.getToFundId())));

    if (!creditBudgetsGrouped.isEmpty()) {
      log.debug("Calculating budget totals for credit transactions");
      creditBudgetsGrouped.entrySet()
        .forEach(this::updateBudgetCreditTotals);
    }

    return groupedBudgets;
  }

  private Map<String, Budget> calculateNonEncumbranceBudgetTotals(List<Transaction> tempTransactions,
      Map<String, Budget> groupedBudgets) {
    Map<Budget, List<Transaction>> noEncumbranceBudgetsGrouped = tempTransactions.stream()
      .filter(txn -> txn.getPaymentEncumbranceId() == null)
      .collect(groupingBy(txn -> groupedBudgets.get(isCreditTransaction.test(txn) ? txn.getToFundId() : txn.getFromFundId())));

    if (!noEncumbranceBudgetsGrouped.isEmpty()) {
      log.info("Calculating budget totals for payments/credits with no Encumbrances attached");
      noEncumbranceBudgetsGrouped.entrySet()
        .forEach(this::updateBudgetNoEncumbranceTotals);
    }

    return groupedBudgets;

  }

  private Budget updateBudgetPaymentTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    CurrencyUnit currency = Monetary.getCurrency(entry.getValue()
      .get(0)
      .getCurrency());
    entry.getValue()
      .forEach(txn -> {
        budget.setExpenditures(MoneyUtils.sumMoney(budget.getExpenditures(), txn.getAmount(), currency));
        budget.setAwaitingPayment(MoneyUtils.subtractMoney(budget.getAwaitingPayment(), txn.getAmount(), currency));
      });

    return budget;
  }

  private Budget updateBudgetCreditTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    CurrencyUnit currency = Monetary.getCurrency(entry.getValue()
      .get(0)
      .getCurrency());
    entry.getValue()
      .forEach(txn -> {
        budget.setExpenditures(MoneyUtils.subtractMoneyNonNegative(budget.getExpenditures(), txn.getAmount(), currency));
        budget.setEncumbered(MoneyUtils.sumMoney(budget.getEncumbered(), txn.getAmount(), currency));
      });
    return budget;
  }

  /**
   * For the payments and credits that do not have corresponding encumbrances, the available and unavailable amounts in the budget
   * are re-calculated
   *
   * @param entry- Budget with list of transactions that do have encumbrances
   */
  private Budget updateBudgetNoEncumbranceTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    CurrencyUnit currency = Monetary.getCurrency(entry.getValue()
      .get(0)
      .getCurrency());
    entry.getValue()
      .forEach(txn -> {
        if (isCreditTransaction.test(txn)) {
          budget.setAvailable(MoneyUtils.sumMoney(budget.getAvailable(), txn.getAmount(), currency));
          budget.setUnavailable(MoneyUtils.subtractMoney(budget.getUnavailable(), txn.getAmount(), currency));
        } else if (isPaymentTransaction.test(txn)) {
          budget.setAvailable(MoneyUtils.subtractMoney(budget.getAvailable(), txn.getAmount(), currency));
          budget.setUnavailable(MoneyUtils.sumMoney(budget.getUnavailable(), txn.getAmount(), currency));
        }

      });
    return budget;
  }

  /**
   * Update the Encumbrance transaction attached to the payment/Credit(from paymentEncumbranceID)
   * in a transaction
   *
   * @param tx : the list of payments and credits
   */
  private Future<Tx<List<Transaction>>> updateEncumbranceTotals(Tx<List<Transaction>> tx) {
    boolean noEncumbrances = tx.getEntity()
      .stream()
      .allMatch(tx1 -> StringUtils.isBlank(tx1.getPaymentEncumbranceId()));

    if (noEncumbrances) {
      return Future.succeededFuture(tx);
    }

    return getAllEncumbrances(tx).map(encumbrances -> encumbrances.stream()
      .collect(toMap(Transaction::getId, Function.identity())))
      .map(encumbrancesMap -> applyPayments(tx.getEntity(), encumbrancesMap))
      .map(encumbrancesMap -> applyCredits(tx.getEntity(), encumbrancesMap))
      //update all the re-calculated encumbrances into the Transaction table
      .map(map -> new Tx<>(map.values()
        .stream()
        .collect(Collectors.toList()), tx.getPgClient()).withConnection(tx.getConnection()))
      .compose(this::updatePermanentTransactions)
      .compose(ok -> Future.succeededFuture(tx));

  }

  /**
   * <pre>
   * Encumbrances are recalculated with the credit transaction as below:
   * - expended decreases by credit transaction amount (min 0)
   * - amount (effectiveEncumbrance) is recalculated= (initialAmountEncumbered - (amountAwaitingPayment + amountExpended))
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

      // recalculate effectiveEncumbrance ( initialAmountEncumbered - (amountAwaitingPayment + amountExpended))
      double newAmount = MoneyUtils.subtractMoney(encumbranceTxn.getEncumbrance()
        .getInitialAmountEncumbered(),
          MoneyUtils.sumMoney(encumbranceTxn.getEncumbrance()
            .getAmountAwaitingPayment(),
              encumbranceTxn.getEncumbrance()
                .getAmountExpended(),
              currency),
          currency);
      encumbranceTxn.setAmount(newAmount);
    });

    return encumbrancesMap;
  }

  /**
   * <pre>
   * Encumbrances are recalculated with the payment transaction as below:
   * - awaitingPayment decreases by payment transaction amount
   * - expended increases by payment transaction amount
   * - amount decreases by the payment transaction amount
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
      double newAwaitingPayment = MoneyUtils.subtractMoney(encumbranceTxn.getEncumbrance()
        .getAmountAwaitingPayment(), pymtTxn.getAmount(), currency);
      double newExpended = MoneyUtils.sumMoney(encumbranceTxn.getEncumbrance()
        .getAmountExpended(), pymtTxn.getAmount(), currency);
      double newAmount = MoneyUtils.subtractMoneyNonNegative(encumbranceTxn.getAmount(), pymtTxn.getAmount(), currency);

      encumbranceTxn.getEncumbrance()
        .setAmountAwaitingPayment(newAwaitingPayment);
      encumbranceTxn.getEncumbrance()
        .setAmountExpended(newExpended);
      encumbranceTxn.setAmount(newAmount);
    });

    return encumbrancesMap;
  }

  private Future<List<Transaction>> getAllEncumbrances(Tx<List<Transaction>> tx) {
    Promise<List<Transaction>> promise = Promise.promise();
    String sql = "SELECT DISTINCT ON (permtransactions.id) permtransactions.jsonb " + "FROM "
        + getFullTableName(getTenantId(), TRANSACTIONS_TABLE) + " AS permtransactions " + "INNER JOIN "
        + getFullTemporaryTransactionTableName() + " AS transactions "
        + "ON transactions.paymentEncumbranceId = permtransactions.id" + " WHERE transactions.sourceInvoiceId = ?";
    JsonArray params = new JsonArray();
    params.add(getSummaryId(tx.getEntity()
      .get(0)));
    tx.getPgClient()
      .select(tx.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Transaction> encumbrances = reply.result()
            .getResults()
            .stream()
            .flatMap(JsonArray::stream)
            .map(o -> new JsonObject(o.toString()).mapTo(Transaction.class))
            .collect(Collectors.toList());
          promise.complete(encumbrances);
        }
      });
    return promise.future();
  }



  @Override
  public void updateTransaction(Transaction transaction) {
    throw new UnsupportedOperationException("Payments and credits are Immutable");
  }

  @Override
  void handleValidationError(Transaction transaction) {

    List<Error> errors = new ArrayList<>();

    if (isCreditTransaction.test(transaction)) {
      errors.addAll(buildNullValidationError(transaction.getToFundId(), "toFundId"));
    } else {
      errors.addAll(buildNullValidationError(transaction.getFromFundId(), "fromFundId"));
    }
    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }

  }

  @Override
  String createTempTransactionQuery() {
    return "INSERT INTO " + getFullTemporaryTransactionTableName() + " (id, jsonb) VALUES (?, ?::JSON) ";
  }

  @Override
  String createPermanentTransactionsQuery() {
    return String.format("INSERT INTO %s (id, jsonb) " + "SELECT id, jsonb FROM %s WHERE sourceInvoiceId = ? "
        + "ON CONFLICT DO NOTHING;", getFullTransactionTableName(), getTemporaryTransactionTable());

  }

  @Override
  int getSummaryCount(JsonObject summary){
    return summary.getInteger("numPaymentsCredits");
  }

  @Override
  protected String getBudgetsQuery(){
   return "SELECT DISTINCT ON (budgets.id) budgets.jsonb " + "FROM " + getFullTableName(getTenantId(), BUDGET_TABLE)
    + " AS budgets " + "INNER JOIN " + getFullTemporaryTransactionTableName() + " AS transactions "
    + "ON ((budgets.fundId = transactions.fromFundId OR budgets.fundId = transactions.toFundId) AND transactions.fiscalYearId = budgets.fiscalYearId) "
    + "WHERE transactions.sourceInvoiceId = ?";
  }

}
