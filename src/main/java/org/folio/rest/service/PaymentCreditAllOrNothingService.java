package org.folio.rest.service;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.impl.TransactionSummaryAPI.INVOICE_TRANSACTION_SUMMARIES;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.MoneyUtils.subtractMoney;
import static org.folio.rest.persist.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.rest.persist.MoneyUtils.sumMoney;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.ws.rs.core.Response;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.dao.PaymentsCreditsTransactionalDAO;
import org.folio.rest.dao.TemporaryTransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.MoneyUtils;
import org.folio.rest.persist.Tx;
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

public class PaymentCreditAllOrNothingService extends AllOrNothingTransactionService {

  private static final String TEMPORARY_INVOICE_TRANSACTIONS = "temporary_invoice_transactions";
  private static final String TRANSACTIONS_TABLE = "transaction";

  private PaymentsCreditsTransactionalDAO paymentsCreditsDAO;

  public static final String SELECT_PERMANENT_TRANSACTIONS = "SELECT DISTINCT ON (permtransactions.id) permtransactions.jsonb FROM %s AS permtransactions INNER JOIN %s AS transactions "
    + "ON transactions.paymentEncumbranceId = permtransactions.id WHERE transactions.sourceInvoiceId = ?";

  Predicate<Transaction> hasEncumbrance = txn -> txn.getPaymentEncumbranceId() != null;
  Predicate<Transaction> isPaymentTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.PAYMENT);
  Predicate<Transaction> isCreditTransaction = txn -> txn.getTransactionType().equals(Transaction.TransactionType.CREDIT);

  public PaymentCreditAllOrNothingService(TemporaryTransactionDAO temporaryTransactionDAO) {
    super(temporaryTransactionDAO);
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
      .compose(this::createPermanentTransactions)
      .map(tx);
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
      .collect(toMap(Transaction::getId, identity())))
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
    return getBudgets(tx)
      .map(budgets -> budgets.stream().collect(toMap(Budget::getFundId, Function.identity())))
      .map(groupedBudgets -> calculatePaymentBudgetsTotals(tx.getEntity(), groupedBudgets))
      .map(grpBudgets -> calculateCreditBudgetsTotals(tx.getEntity(), grpBudgets))
      .map(grpBudgets -> calculateNonEncumbranceBudgetTotals(tx.getEntity(), grpBudgets))
      .compose(budgets -> updateBudgets(tx, new ArrayList<>(budgets.values())));
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

  private Future<List<Transaction>> getAllEncumbrances(Tx<List<Transaction>> tx) {
    Promise<List<Transaction>> promise = Promise.promise();
    String sql = buildGetPermanentEncumbrancesQuery();
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

  private String buildGetPermanentEncumbrancesQuery() {
    return String.format(SELECT_PERMANENT_TRANSACTIONS, getFullTableName(getTenantId(), TRANSACTIONS_TABLE),
        getFullTemporaryTransactionTableName());
  }

  private Future<Tx<List<Transaction>>> updateLedgerFYsTotals(Tx<List<Transaction>> tx) {
    return paymentsCreditsDAO.groupTempTransactionsByLedgerFy(tx)
      .map(this::calculateLedgerFyTotals)
      .compose(ledgers -> updateLedgerFYs(tx, ledgers));
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
  public void updateTransaction(Transaction transaction) {
    verifyTransactionExistence(transaction.getId())
      .compose(aVoid -> processAllOrNothing(transaction, this::processTransactionsUponUpdate))
      .onComplete(result -> {
        if (result.failed()) {
          HttpStatusException cause = (HttpStatusException) result.cause();
          switch (cause.getStatusCode()) {
            case 400:
              getAsyncResultHandler().handle(Future.succeededFuture(
                FinanceStorageTransactions.PutFinanceStorageTransactionsByIdResponse.respond400WithTextPlain(cause.getPayload())));
              break;
            case 404:
              getAsyncResultHandler().handle(Future.succeededFuture(
                FinanceStorageTransactions.PutFinanceStorageTransactionsByIdResponse.respond404WithTextPlain(cause.getPayload())));
              break;
            case 422:
              getAsyncResultHandler()
                .handle(Future.succeededFuture(FinanceStorageTransactions.PutFinanceStorageTransactionsByIdResponse
                  .respond422WithApplicationJson(new JsonObject(cause.getPayload()).mapTo(Errors.class))));
              break;
            default:
              getAsyncResultHandler()
                .handle(Future.succeededFuture(FinanceStorageTransactions.PutFinanceStorageTransactionsByIdResponse
                  .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
          }
        } else {
          log.debug("Preparing response to client");
          getAsyncResultHandler()
            .handle(Future.succeededFuture(FinanceStorageTransactions.PutFinanceStorageTransactionsByIdResponse.respond204()));
        }
      });
  }

  private Future<Void> verifyTransactionExistence(String transactionId) {
    Promise<Void> promise = Promise.promise();
    getPostgresClient().getById(TRANSACTION_TABLE, transactionId, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else if (reply.result() == null) {
        promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), "Not found"));
      } else {
        promise.complete();
      }
    });
    return promise.future();
  }

  private Future<Tx<List<Transaction>>> processTransactionsUponUpdate(Tx<List<Transaction>> tx) {

    return getPermanentTransactions(tx)
      .compose(existingTransactions -> updateBudgetsTotals(tx, existingTransactions)
        .map(listTx -> excludeReleasedEncumbrances(tx.getEntity(), existingTransactions)))
      .compose(transactions -> updatePermanentTransactions(tx, transactions));
  }

  private List<Transaction> excludeReleasedEncumbrances(List<Transaction> newTransactions, List<Transaction> existingTransactions) {
    Map<String, Transaction> groupedTransactions = existingTransactions.stream().collect(toMap(Transaction::getId, identity()));
    return newTransactions.stream()
      .filter(transaction -> groupedTransactions.get(transaction.getId()).getEncumbrance().getStatus() != Encumbrance.Status.RELEASED)
      .collect(Collectors.toList());
  }


  private Future<Tx<List<Transaction>>> updateBudgetsTotals(Tx<List<Transaction>> tx, List<Transaction> existingTransactions) {
    return getBudgets(tx)
      .map(budgets -> updateBudgetsTotals(existingTransactions, tx.getEntity(), budgets))
      .compose(budgets -> updateBudgets(tx, budgets));
  }


  private List<Budget> updateBudgetsTotals(List<Transaction> existingTransactions, List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<String, Transaction> existingGrouped = existingTransactions.stream().collect(toMap(Transaction::getId, identity()));
    Map<Budget, List<Transaction>> tempGrouped = groupTransactionsByBudget(tempTransactions, budgets);
    return tempGrouped.entrySet().stream()
      .map(listEntry -> updateBudgetTotals(listEntry, existingGrouped))
      .collect(Collectors.toList());
  }

  private Budget updateBudgetTotals(Map.Entry<Budget, List<Transaction>> entry, Map<String, Transaction> existingGrouped) {
    Budget budget = entry.getKey();

    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {
          Transaction existingTransaction = existingGrouped.get(tmpTransaction.getId());
          if (!isEncumbranceReleased(existingTransaction)) {
            processBudget(budget, currency, tmpTransaction, existingTransaction);
            if (isEncumbranceReleased(tmpTransaction)) {
              releaseEncumbrance(budget, currency, tmpTransaction);
            }
          }
        });
    }
    return budget;
  }

  private void releaseEncumbrance(Budget budget, CurrencyUnit currency, Transaction tmpTransaction) {
    //encumbered decreases by the amount being released
    budget.setEncumbered(subtractMoney(budget.getEncumbered(), tmpTransaction.getAmount(), currency));

    // available increases by the amount being released
    budget.setAvailable(sumMoney(budget.getAvailable(), tmpTransaction.getAmount(), currency));

    // unavailable decreases by the amount being released (min 0)
    double newUnavailable = subtractMoney(budget.getUnavailable(), tmpTransaction.getAmount(), currency);
    budget.setUnavailable(newUnavailable < 0 ? 0 : newUnavailable);

    // transaction.amount becomes 0 (save the original value for updating the budget)
    tmpTransaction.setAmount(0d);
  }

  private void processBudget(Budget budget, CurrencyUnit currency, Transaction tmpTransaction, Transaction existingTransaction) {
    // encumbered decreases by the difference between provided and previous transaction.encumbrance.amountAwaitingPayment values
    double newEncumbered = subtractMoney(budget.getEncumbered(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    newEncumbered = sumMoney(newEncumbered, existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    budget.setEncumbered(newEncumbered);

    // awaitingPayment increases by the same amount
    double newAwaitingPayment = sumMoney(budget.getAwaitingPayment(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    newAwaitingPayment = subtractMoneyNonNegative(newAwaitingPayment, existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    budget.setAwaitingPayment(newAwaitingPayment);

    // encumbrance transaction.amount is updated to (initial encumbrance - awaiting payment - expended)
    double newAmount = subtractMoney(tmpTransaction.getEncumbrance().getInitialAmountEncumbered(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
    newAmount = subtractMoney(newAmount, tmpTransaction.getEncumbrance().getAmountExpended(), currency);
    tmpTransaction.setAmount(newAmount);
  }


  private boolean isEncumbranceReleased(Transaction transaction) {
    return transaction.getEncumbrance()
      .getStatus() == Encumbrance.Status.RELEASED;
  }

  private Map<Budget, List<Transaction>> groupTransactionsByBudget(List<Transaction> existingTransactions, List<Budget> budgets) {
    MultiKeyMap<String, Budget> groupedBudgets = new MultiKeyMap<>();
    groupedBudgets.putAll(budgets.stream().collect(toMap(budget -> new MultiKey<>(budget.getFundId(), budget.getFiscalYearId()), identity())));

    return existingTransactions.stream()
      .collect(groupingBy(
        transaction -> groupedBudgets.get(transaction.getFromFundId(), transaction.getFiscalYearId())));

  }

  private Future<List<Transaction>> getPermanentTransactions(Tx<List<Transaction>> tx) {
    Promise<List<Transaction>> promise = Promise.promise();

    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    tx.getEntity().stream()
      .map(Transaction::getId)
      .forEach(id -> criterionBuilder.with("id", id));

    Criterion criterion = criterionBuilder.build();

    tx.getPgClient().get(tx.getConnection(), TRANSACTION_TABLE, Transaction.class, criterion, false, false, reply -> {
      if (reply.failed()) {
        log.error("Failed to extract permanent transactions", reply.cause());
        handleFailure(promise, reply);
      } else {
        List<Transaction> transactions = reply.result()
          .getResults();
        promise.complete(transactions);
      }
    });
    return promise.future();
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
  String createPermanentTransactionsQuery() {
    return createPermanentTransactionsQuery(INSERT_PERMANENT_PAYMENTS_CREDITS);
  }

  @Override
  protected String getSelectBudgetsQuery(){
    return getSelectBudgetsQuery(SELECT_BUDGETS_BY_INVOICE_ID);
  }

  @Override
  String getSummaryId(Transaction transaction) {
    return transaction.getSourceInvoiceId();
  }

  /**
   * Fetch transactions from temporary table for a given Invoice Id
   */
  @Override
  Criterion getTransactionBySummaryIdCriterion(String value) {
    CriterionBuilder criterionBuilder = new CriterionBuilder().with("sourceInvoiceId", value);
    return criterionBuilder.build();
  }

}
