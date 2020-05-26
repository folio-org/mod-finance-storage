package org.folio.rest.service.transactions;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.dao.transactions.PaymentsCreditsDAO.SELECT_BUDGETS_BY_INVOICE_ID;
import static org.folio.rest.dao.transactions.TemporaryInvoiceTransactionsDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.impl.FinanceStorageAPI.LEDGERFY_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;

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

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.dao.transactions.PaymentsCreditsDAO;
import org.folio.rest.dao.transactions.TemporaryInvoiceTransactionsDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.MoneyUtils;
import org.folio.rest.service.summary.PaymentsCreditsTransactionSummaryService;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class PaymentCreditAllOrNothingService extends AllOrNothingTransactionService<InvoiceTransactionSummary> {

  private static final String TRANSACTIONS_TABLE = "transaction";


  public static final String SELECT_PERMANENT_TRANSACTIONS = "SELECT DISTINCT ON (permtransactions.id) permtransactions.jsonb FROM %s AS permtransactions INNER JOIN %s AS transactions "
    + "ON transactions.paymentEncumbranceId = permtransactions.id WHERE transactions.sourceInvoiceId = ?";

  public static final String SELECT_LEDGERFYS_TRANSACTIONS = "SELECT ledger_fy.jsonb, transactions.jsonb FROM %s AS ledger_fy INNER JOIN %s AS funds"
    + " ON (funds.ledgerId = ledger_fy.ledgerId) INNER JOIN %s AS transactions"
    + " ON (funds.id = transactions.fromFundId OR funds.id = transactions.toFundId)"
    + " WHERE (transactions.sourceInvoiceId = ? AND ledger_fy.fiscalYearId = transactions.fiscalYearId AND transactions.paymentEncumbranceId IS NULL);";

  Predicate<Transaction> hasEncumbrance = txn -> txn.getPaymentEncumbranceId() != null;
  Predicate<Transaction> isPaymentTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.PAYMENT);
  Predicate<Transaction> isCreditTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.CREDIT);

  public PaymentCreditAllOrNothingService() {
    super(new TemporaryInvoiceTransactionsDAO(), new PaymentsCreditsTransactionSummaryService(), new PaymentsCreditsDAO());
  }


  /**
   * Before the temporary transaction can be moved to permanent transaction table, the corresponding fields in encumbrances and
   * budgets for payments/credits must be recalculated. To avoid partial transactions, all payments and credits will be done in a
   * database transaction
   *
   * @param client
   */
  @Override
  public Future<Void> processTemporaryToPermanentTransactions(List<Transaction> transactions, DBClient client) {
    String summaryId = getSummaryId(transactions.get(0));
    return updateEncumbranceTotals(transactions, client)
      .compose(dbc -> updateBudgetsTotals(transactions, client))
      .compose(dbc -> updateLedgerFYsTotals(summaryId, client))
      .compose(dbc -> transactionsDAO.saveTransactionsToPermanentTable(summaryId, client))
      .mapEmpty();
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
    JsonArray params = new JsonArray();
    params.add(summaryId);
    String sql = getSelectBudgetsQuery(client.getTenantId());
    return budgetDAO.getBudgets(sql, params, client)
      .map(budgets -> budgets.stream().collect(toMap(Budget::getFundId, Function.identity())))
      .map(groupedBudgets -> calculatePaymentBudgetsTotals(transactions, groupedBudgets))
      .map(grpBudgets -> calculateCreditBudgetsTotals(transactions, grpBudgets))
      .map(grpBudgets -> calculateNonEncumbranceBudgetTotals(transactions, grpBudgets))
      .map(budgets -> budgets.values().stream().map(JsonObject::mapFrom).collect(toList()))
      .compose(jsonBudgets -> budgetDAO.updateBatchBudgets(buildUpdateBudgetsQuery(jsonBudgets, client.getTenantId()), client));
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

  private void updateBudgetPaymentTotals(Map.Entry<Budget, List<Transaction>> entry) {
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
  }

  private void updateBudgetCreditTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    CurrencyUnit currency = Monetary.getCurrency(entry.getValue()
      .get(0)
      .getCurrency());
    entry.getValue()
      .forEach(txn -> {
        budget.setExpenditures(MoneyUtils.subtractMoneyNonNegative(budget.getExpenditures(), txn.getAmount(), currency));
        budget.setEncumbered(MoneyUtils.sumMoney(budget.getEncumbered(), txn.getAmount(), currency));
      });
  }

  /**
   * For the payments and credits that do not have corresponding encumbrances, the available and unavailable amounts in the budget
   * are re-calculated
   *
   * @param entry- Budget with list of transactions that do have encumbrances
   */
  private void updateBudgetNoEncumbranceTotals(Map.Entry<Budget, List<Transaction>> entry) {
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

  private Future<List<Transaction>> getAllEncumbrances(String summaryId, DBClient client) {
    Promise<List<Transaction>> promise = Promise.promise();
    String sql = buildGetPermanentEncumbrancesQuery(client.getTenantId());
    JsonArray params = new JsonArray();
    params.add(summaryId);
    client.getPgClient()
      .select(client.getConnection(), sql, params, reply -> {
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

  private String buildGetPermanentEncumbrancesQuery(String tenantId) {
    return String.format(SELECT_PERMANENT_TRANSACTIONS, getFullTableName(tenantId, TRANSACTIONS_TABLE),
        getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
  }

  private Future<Void> updateLedgerFYsTotals(String summaryId, DBClient client) {
    return groupTempTransactionsByLedgerFy(summaryId, client)
      .map(this::calculateLedgerFyTotals)
      .compose(ledgers -> updateLedgerFYs(ledgers, client));
  }

  public Future<Map<LedgerFY, List<Transaction>>> groupTempTransactionsByLedgerFy(String summaryId, DBClient client) {
    Promise<Map<LedgerFY, List<Transaction>>> promise = Promise.promise();
    String sql = getLedgerFYsQuery(client.getTenantId());
    JsonArray params = new JsonArray();
    params.add(summaryId);
    client.getPgClient()
      .select(client.getConnection(), sql, params, reply -> {
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

  public Collector<JsonArray, ?, HashMap<LedgerFY, List<Transaction>>> ledgerFYTransactionsMapping() {
    int ledgerFYColumnNumber = 0;
    int transactionsColumnNumber = 1;
    return toMap(o -> new JsonObject(o.getString(ledgerFYColumnNumber))
      .mapTo(LedgerFY.class), o -> Collections.singletonList(new JsonObject(o.getString(transactionsColumnNumber))
      .mapTo(Transaction.class)), (o, o2) -> {
      List<Transaction> newList = new ArrayList<>(o); newList.addAll(o2); return newList;}, HashMap::new);
  }

  private String getLedgerFYsQuery(String tenantId) {
    return String.format(SELECT_LEDGERFYS_TRANSACTIONS,
      getFullTableName(tenantId, LEDGERFY_TABLE), getFullTableName(tenantId, FUND_TABLE),
      getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
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


  @Override
  Void handleValidationError(Transaction transaction) {

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


}
