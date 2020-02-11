package org.folio.rest.transaction;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FinanceStorageAPI.LEDGERFY_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.impl.TransactionSummaryAPI.INVOICE_TRANSACTION_SUMMARIES;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.handleFailure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.MoneyUtils;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class PaymentCreditHandler extends AllOrNothingHandler {

  private static final String TEMPORARY_INVOICE_TRANSACTIONS = "temporary_invoice_transactions";
  private static final String TRANSACTIONS_TABLE = "transaction";

  Predicate<Transaction> hasEncumbrance = txn -> txn.getPaymentEncumbranceId() != null;
  Predicate<Transaction> isPaymentTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.PAYMENT);
  Predicate<Transaction> isCreditTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.CREDIT);

  public PaymentCreditHandler(Map<String, String> okapiHeaders, Context ctx, Handler<AsyncResult<Response>> asyncResultHandler) {
    super(TEMPORARY_INVOICE_TRANSACTIONS, INVOICE_TRANSACTION_SUMMARIES, okapiHeaders, ctx, asyncResultHandler);
  }

  /**
   * Before the temporary transaction can be moved to permanent transaction table, the corresponding fields in encumbrances and
   * budgets for payments/credits must be recalculated. To avoid partial transactions, all payments and credits will be done in a
   * database transaction
   *
   * @param tx
   */
  @Override
  public Future<Tx<List<Transaction>>> processTemporaryToPermanentTransactions(Tx<List<Transaction>> tx) {
    return updateEncumbranceTotals(tx).compose(this::updateBudgetsTotals)
      .compose(this::updateLedgerFYsTotals)
      .compose(this::createPermanentTransactions);
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


  private Future<Tx<List<Transaction>>> updateBudgetsTotals(Tx<List<Transaction>> tx) {
    return getBudgets(tx).map(budgets -> budgets.stream()
      .collect(toMap(Budget::getFundId, Function.identity())))
      .map(groupedBudgets -> calculatePaymentBudgetsTotals(tx.getEntity(), groupedBudgets))
      .map(grpBudgets -> calculateCreditBudgetsTotals(tx.getEntity(), grpBudgets))
      .map(grpBudgets -> calculateNonEncumbranceBudgetTotals(tx.getEntity(), grpBudgets))
      .compose(budgets -> updateBudgets(tx, new ArrayList<Budget>(budgets.values())));
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
        double newAwaitingPayment = MoneyUtils.subtractMoneyNonNegative(budget.getAwaitingPayment(), txn.getAmount(), currency);
        budget.setAwaitingPayment(newAwaitingPayment);
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
          budget.setExpenditures(MoneyUtils.subtractMoneyNonNegative(budget.getExpenditures(), txn.getAmount(), currency));
        } else if (isPaymentTransaction.test(txn)) {
          budget.setAvailable(MoneyUtils.subtractMoney(budget.getAvailable(), txn.getAmount(), currency));
          budget.setUnavailable(MoneyUtils.sumMoney(budget.getUnavailable(), txn.getAmount(), currency));
          budget.setExpenditures(MoneyUtils.sumMoney(budget.getExpenditures(), txn.getAmount(), currency));
        }

      });
    return budget;
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

  private Future<Tx<List<Transaction>>> updateLedgerFYsTotals(Tx<List<Transaction>> tx) {
    return groupTempTransactionsByLedgerFy(tx)
      .map(this::calculateLedgerFyTotals)
      .compose(ledgers -> updateLedgerFYs(tx, ledgers));
  }

  private Future<Map<LedgerFY, List<Transaction>>> groupTempTransactionsByLedgerFy(Tx<List<Transaction>> tx) {
    Promise<Map<LedgerFY, List<Transaction>>> promise = Promise.promise();
    String sql = getLedgersQuery();
    JsonArray params = new JsonArray();
    params.add(getSummaryId(tx.getEntity()
      .get(0)));
    tx.getPgClient()
      .select(tx.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          Map<LedgerFY, List<Transaction>> ledgers = reply.result()
            .getResults()
            .stream()
            .collect(ledgerFYTransactionsMapping());
          promise.complete(ledgers);
        }
      });
    return promise.future();
  }

  private Collector<JsonArray, ?, HashMap<LedgerFY, List<Transaction>>> ledgerFYTransactionsMapping() {
    int ledgerFYColumnNumber = 0;
    int transactionsColumnNumber = 1;
    return toMap(o -> new JsonObject(o.getString(ledgerFYColumnNumber))
      .mapTo(LedgerFY.class), o -> Collections.singletonList(new JsonObject(o.getString(transactionsColumnNumber))
      .mapTo(Transaction.class)), (o, o2) -> {
      List<Transaction> newList = new ArrayList<>(o); newList.addAll(o2); return newList;}, HashMap::new);
  }

  private List<LedgerFY> calculateLedgerFyTotals(Map<LedgerFY, List<Transaction>> groupedLedgerFYs) {
    return groupedLedgerFYs.entrySet().stream().map(this::updateLedgerFY).collect(toList());
  }

  private LedgerFY updateLedgerFY(Map.Entry<LedgerFY, List<Transaction>> ledgerFYListEntry) {
    LedgerFY ledgerFY = ledgerFYListEntry.getKey();
    MonetaryAmount totalAmount = getTotalTransactionsAmount(ledgerFYListEntry);
    double newAvailable = Math.max(Money.of(ledgerFY.getAvailable(), ledgerFY.getCurrency()).add(totalAmount).getNumber().doubleValue(), 0d);
    double newUnavailable = Math.max(Money.of(ledgerFY.getUnavailable(), ledgerFY.getCurrency()).subtract(totalAmount).getNumber().doubleValue(), 0d);

    return ledgerFY
      .withAvailable(newAvailable)
      .withUnavailable(newUnavailable);
  }

  private MonetaryAmount getTotalTransactionsAmount(Map.Entry<LedgerFY, List<Transaction>> ledgerFYListEntry) {
    return ledgerFYListEntry.getValue().stream()
      .map(transaction -> (MonetaryAmount) Money.of(transaction.getTransactionType() == Transaction.TransactionType.PAYMENT ? - transaction.getAmount() : transaction.getAmount(), transaction.getCurrency()))
      .reduce(MonetaryFunctions::sum).orElse(Money.zero(Monetary.getCurrency(ledgerFYListEntry.getKey().getCurrency())));
  }

  private Future<Tx<List<Transaction>>> updateLedgerFYs(Tx<List<Transaction>> tx, List<LedgerFY> ledgerFYs) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    if (ledgerFYs.isEmpty()) {
      promise.complete(tx);
    } else {
      List<JsonObject> jsonLedgerFYs = ledgerFYs.stream().map(JsonObject::mapFrom).collect(Collectors.toList());
      String sql = "UPDATE " + getFullTableName(getTenantId(), LEDGERFY_TABLE) + " AS ledger_fy " +
        "SET jsonb = b.jsonb FROM (VALUES  " + getValues(jsonLedgerFYs) + ") AS b (id, jsonb) " +
        "WHERE b.id::uuid = ledger_fy.id;";
      tx.getPgClient().execute(tx.getConnection(), sql, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(tx);
        }
      });
    }
    return promise.future();
  }

  private String getLedgersQuery(){
    return "SELECT ledger_fy.jsonb, transactions.jsonb FROM " + getFullTableName(getTenantId(), LEDGERFY_TABLE)
      + " AS ledger_fy INNER JOIN " + getFullTableName(getTenantId(), FUND_TABLE) + " AS funds"
      + " ON (funds.ledgerId = ledger_fy.ledgerId)"
      + " INNER JOIN " + getFullTemporaryTransactionTableName() + " AS transactions"
      + " ON (funds.id = transactions.fromFundId OR funds.id = transactions.toFundId)"
      + " WHERE (transactions.sourceInvoiceId = ? AND ledger_fy.fiscalYearId = transactions.fiscalYearId AND transactions.paymentEncumbranceId IS NULL);";
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
    return "INSERT INTO " + getFullTemporaryTransactionTableName() + " (id, jsonb) VALUES (?, ?::JSON) "
        + "ON CONFLICT (lower(f_unaccent(jsonb ->> 'amount'::text)), " + "lower(f_unaccent(jsonb ->> 'fromFundId'::text)), "
        + "lower(f_unaccent(jsonb ->> 'sourceInvoiceId'::text)), "
        + "lower(f_unaccent(jsonb ->> 'sourceInvoiceLineId'::text)), "
        + "lower(f_unaccent(jsonb ->> 'toFundId'::text)), "
        + "lower(f_unaccent(jsonb ->> 'transactionType'::text))) " + "DO UPDATE SET id = excluded.id RETURNING id;";
  }

  @Override
  String createPermanentTransactionsQuery() {
    return String.format("INSERT INTO %s (id, jsonb) " + "SELECT id, jsonb FROM %s WHERE sourceInvoiceId = ? AND jsonb ->> 'transactionType' != 'Encumbrance'"
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

  @Override
  String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  /**
   * Fetch only payments and credits from temporary table for a given Invoice Id
   */
  @Override
  Criterion getTransactionBySummaryIdCriterion(String value) {
    Criteria criteria = HelperUtils.getCriteriaByFieldNameAndValue("sourceInvoiceId", "=", value);
    Criteria criteria1 = HelperUtils.getCriteriaByFieldNameAndValue("transactionType", "!=", "Encumbrance");

    return new Criterion().addCriterion(criteria, "AND", criteria1);
  }

}
