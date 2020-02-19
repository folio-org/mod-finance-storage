package org.folio.rest.transaction;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FinanceStorageAPI.LEDGERFY_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.impl.TransactionSummaryAPI.ORDER_TRANSACTION_SUMMARIES;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.handleFailure;
import static org.folio.rest.persist.MoneyUtils.subtractMoney;
import static org.folio.rest.persist.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.rest.persist.MoneyUtils.sumMoney;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.ws.rs.core.Response;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
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


public class EncumbranceHandler extends AllOrNothingHandler {

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";

  public EncumbranceHandler(Map<String, String> okapiHeaders, Context ctx, Handler<AsyncResult<Response>> asyncResultHandler) {
    super(TEMPORARY_ORDER_TRANSACTIONS, ORDER_TRANSACTION_SUMMARIES, okapiHeaders, ctx, asyncResultHandler);
  }

  @Override
  public void updateTransaction(Transaction transaction) {
    verifyTransactionExistence(transaction.getId())
      .compose(aVoid -> processAllOrNothing(transaction, this::processTransactionsUponUpdate))
      .setHandler(result -> {
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

  @Override
  String getSummaryId(Transaction transaction) {
    return Optional.ofNullable(transaction.getEncumbrance())
      .map(Encumbrance::getSourcePurchaseOrderId)
      .orElse(null);
  }

  @Override
  Criterion getTransactionBySummaryIdCriterion(String value) {
    return getCriterionByFieldNameAndValueNotJsonb("encumbrance_sourcePurchaseOrderId", "=", value);
  }

  @Override
  void handleValidationError(Transaction transaction) {
    List<Error> errors = new ArrayList<>();

    errors.addAll(buildNullValidationError(getSummaryId(transaction), "encumbrance"));
    errors.addAll(buildNullValidationError(transaction.getFromFundId(), "fromFundId"));

    if (isNotEmpty(errors)) {
      throw new HttpStatusException(422, JsonObject.mapFrom(new Errors().withErrors(errors)
        .withTotalRecords(errors.size()))
        .encode());
    }
  }

  @Override
  String createTempTransactionQuery() {
    return String.format("INSERT INTO %s (id, jsonb) VALUES (?, ?::JSON) "
        + "ON CONFLICT (lower(f_unaccent(jsonb ->> 'amount'::text)), lower(f_unaccent(jsonb ->> 'fromFundId'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePurchaseOrderId'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePoLineId'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'initialAmountEncumbered'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'status'::text))) DO UPDATE SET id = excluded.id RETURNING id;",
        getFullTemporaryTransactionTableName());
  }

  @Override
  String createPermanentTransactionsQuery() {
    return String.format("INSERT INTO %s (id, jsonb) SELECT id, jsonb FROM %s WHERE encumbrance_sourcePurchaseOrderId = ? "
        + "ON CONFLICT DO NOTHING;", getFullTransactionTableName(), getTemporaryTransactionTable());
  }

  private Future<Tx<List<Transaction>>> processTransactionsUponUpdate(Tx<List<Transaction>> tx) {

    List<Transaction> tempTransactions = tx.getEntity();

    return getPermanentTransactions(getSummaryId(tempTransactions.get(0)))
      .compose(existingTransactions -> updateBudgetsWithTotals(tx, existingTransactions))
      .compose(this::updatePermanentTransactions);
  }

  private Future<Tx<List<Transaction>>> updateBudgetsWithTotals(Tx<List<Transaction>> tx, List<Transaction> existingTransactions) {
    return getBudgets(tx)
      .map(budgets -> updateBudgetsTotals(existingTransactions, tx.getEntity(), budgets))
      .compose(budgets -> updateBudgets(tx, budgets));
  }

  private Future<Tx<List<Transaction>>> updateBudgetsWithTotals(Tx<List<Transaction>> tx) {
    return getBudgets(tx)
      .compose(oldBudgets -> {
        List<Budget> newBudgets = updateBudgetsTotals(tx.getEntity(), oldBudgets);
        return updateBudgets(tx, newBudgets)
          .compose(listTx -> updateLedgerFYsWithTotals(listTx, oldBudgets, newBudgets));
      });
  }

  @Override
  protected String getBudgetsQuery() {
    return String.format(
        "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
            + "ON transactions.fromFundId = budgets.fundId AND transactions.fiscalYearId = budgets.fiscalYearId "
            + "WHERE transactions.jsonb -> 'encumbrance' ->> 'sourcePurchaseOrderId' = ?",
        getFullTableName(getTenantId(), BUDGET_TABLE), getFullTemporaryTransactionTableName());
  }

  private List<Budget> updateBudgetsTotals(List<Transaction> existingTransactions, List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<String, Transaction> existingGrouped = existingTransactions.stream().collect(toMap(Transaction::getId, Function.identity()));
    Map<Budget, List<Transaction>> tempGrouped = groupTransactionsByBudget(tempTransactions, budgets);
    return tempGrouped.entrySet().stream()
      .map(listEntry -> updateBudgetTotals(listEntry, existingGrouped))
      .collect(toList());
  }

  private List<Budget> updateBudgetsTotals(List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<Budget, List<Transaction>> tempGrouped = groupTransactionsByBudget(tempTransactions, budgets);
    return tempGrouped.entrySet().stream()
      .map(this::updateBudgetTotals)
      .collect(toList());
  }

  private Budget updateBudgetTotals(Map.Entry<Budget, List<Transaction>> entry, Map<String, Transaction> existingGrouped) {
    Budget budget = entry.getKey();

    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {
          Transaction existingTransaction = existingGrouped.get(tmpTransaction.getId());
          if (!isEncumbranceReleased(existingTransaction)) {
            if (isEncumbranceReleased(tmpTransaction)) {
              releaseEncumbrance(budget, currency, tmpTransaction);
            } else {

              double newEncumbered = sumMoney(budget.getEncumbered(), existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
              newEncumbered = subtractMoneyNonNegative(newEncumbered, tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
              double newAmount = subtractMoney(tmpTransaction.getEncumbrance().getInitialAmountEncumbered(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
              newAmount = subtractMoney(newAmount, tmpTransaction.getEncumbrance().getAmountExpended(), currency);
              double newAwaitingPayment = sumMoney(budget.getAwaitingPayment(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
              newAwaitingPayment = subtractMoneyNonNegative(newAwaitingPayment, existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);

              budget.setEncumbered(newEncumbered);
              budget.setAwaitingPayment(newAwaitingPayment);

              recalculateOverEncumbered(budget, currency);
              tmpTransaction.setAmount(newAmount);
            }
          }
        });
    }
    return budget;
  }

  private Budget updateBudgetTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = JsonObject.mapFrom(entry.getKey()).mapTo(Budget.class);
    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {

          double newEncumbered = sumMoney(budget.getEncumbered(), tmpTransaction.getAmount(), currency);
          budget.setEncumbered(newEncumbered);

          recalculateOverEncumbered(budget, currency);

          recalculateAvailableUnavailable(budget, currency);

        });
    }
    return budget;
  }

  private void recalculateOverEncumbered(Budget budget, CurrencyUnit currency) {
    double a = subtractMoneyNonNegative(budget.getAllocated(), budget.getExpenditures(), currency);
    a = subtractMoneyNonNegative(a, budget.getAwaitingPayment(), currency);
    double newOverEncumbrance = subtractMoneyNonNegative(budget.getEncumbered(), a, currency);
    budget.setOverEncumbrance(newOverEncumbrance);
  }

  private void releaseEncumbrance(Budget budget, CurrencyUnit currency, Transaction tmpTransaction) {

    double newEncumbered = subtractMoneyNonNegative(budget.getEncumbered(), tmpTransaction.getAmount(), currency);
    budget.setEncumbered(newEncumbered);

    recalculateOverEncumbered(budget, currency);
    recalculateAvailableUnavailable(budget, currency);

    tmpTransaction.setAmount(0d);
  }

  private void recalculateAvailableUnavailable(Budget budget, CurrencyUnit currency) {
    double newUnavailable = sumMoney(currency, budget.getEncumbered(), budget.getAwaitingPayment(), budget.getExpenditures(),
      -budget.getOverEncumbrance(), -budget.getOverExpended());
    double newAvailable = subtractMoneyNonNegative(budget.getAllocated(), newUnavailable, currency);

    budget.setAvailable(newAvailable);
    budget.setUnavailable(newUnavailable);
  }

  private boolean isEncumbranceReleased(Transaction tmpTransaction) {
    return tmpTransaction.getEncumbrance()
      .getStatus() == Encumbrance.Status.RELEASED;
  }

  private Map<Budget, List<Transaction>> groupTransactionsByBudget(List<Transaction> existingTransactions, List<Budget> budgets) {
    MultiKeyMap<String, Budget> groupedBudgets = new MultiKeyMap<>();

    groupedBudgets.putAll(budgets.stream().collect(toMap(budget -> new MultiKey<>(budget.getFundId(), budget.getFiscalYearId()), Function.identity())));

    return existingTransactions.stream()
      .collect(groupingBy(
          transaction -> groupedBudgets.get(transaction.getFromFundId(), transaction.getFiscalYearId())));

  }

  private Future<List<Transaction>> getPermanentTransactions(String summaryId) {
    Promise<List<Transaction>> promise = Promise.promise();

    Criterion criterion = new Criterion(getCriteriaByFieldNameAndValue("encumbrance", "=", summaryId).addField("'sourcePurchaseOrderId'"));

    getPostgresClient().get(TRANSACTION_TABLE, Transaction.class, criterion, false, false, reply -> {
      if (reply.failed()) {
        log.error("Failed to extract permanent transaction by purchaseOrderId id={}", reply.cause(), summaryId);
        handleFailure(promise, reply);
      } else {
        List<Transaction> transactions = reply.result()
          .getResults();
        promise.complete(transactions);
      }
    });
    return promise.future();
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

  @Override
  int getSummaryCount(JsonObject summary){
    return summary.getInteger("numTransactions");
  }

  /**
   * To prevent partial encumbrance transactions for an order, all the encumbrances must be created following All or nothing
   */
  @Override
  Future<Tx<List<Transaction>>> processTemporaryToPermanentTransactions(Tx<List<Transaction>> tx) {
    return createPermanentTransactions(tx)
      .compose(updated -> {
        if (updated > 0) {
          return updateBudgetsWithTotals(tx);
        }
        return Future.succeededFuture(tx);
      });
  }

  protected Future<Tx<List<Transaction>>> updateLedgerFYsWithTotals(Tx<List<Transaction>> tx, List<Budget> oldBudgets, List<Budget> newBudgets) {
    String currency = tx.getEntity().get(0).getCurrency();
    Map<String, MonetaryAmount> oldAvailableByFundId = oldBudgets.stream().collect(groupingBy(Budget::getFundId, sumAvailable(currency)));
    Map<String, MonetaryAmount> oldUnavailableByFundId = oldBudgets.stream().collect(groupingBy(Budget::getFundId, sumUnavailable(currency)));

    Map<String, MonetaryAmount> newAvailableByFundId = newBudgets.stream().collect(groupingBy(Budget::getFundId, sumAvailable(currency)));
    Map<String, MonetaryAmount> newUnavailableByFundId = newBudgets.stream().collect(groupingBy(Budget::getFundId, sumUnavailable(currency)));

    Map<String, MonetaryAmount> availableDifference = getAmountDifference(oldAvailableByFundId, newAvailableByFundId);

    Map<String, MonetaryAmount> unavailableDifference = getAmountDifference(oldUnavailableByFundId, newUnavailableByFundId);

    return groupFundIdsByLedgerFy(tx)
      .map(ledgerFYListMap -> calculateLedgerFyTotals(ledgerFYListMap, availableDifference, unavailableDifference))
      .compose(ledgers -> updateLedgerFYs(tx, ledgers));
  }

  private Map<String, MonetaryAmount> getAmountDifference(Map<String, MonetaryAmount> oldAvailableByFundId, Map<String, MonetaryAmount> newAvailableByFundId) {
    return oldAvailableByFundId.entrySet().stream()
      .map(entry -> {
        MonetaryAmount diff = entry.getValue().subtract(newAvailableByFundId.get(entry.getKey()));
        entry.setValue(diff);
        return entry;
      })
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Collector<Budget, ?, MonetaryAmount> sumAvailable(String currency) {
    return Collectors.mapping(budget -> Money.of(budget.getAvailable(), currency),
      Collectors.reducing(Money.of(0, currency), MonetaryFunctions::sum));
  }

  private Collector<Budget, ?, MonetaryAmount> sumUnavailable(String currency) {
    return Collectors.mapping(budget -> Money.of(budget.getUnavailable(), currency),
      Collectors.reducing(Money.of(0, currency), MonetaryFunctions::sum));
  }

  private List<LedgerFY> calculateLedgerFyTotals(Map<LedgerFY, Set<String>> groupedLedgerFYs, Map<String, MonetaryAmount> availableDifference, Map<String, MonetaryAmount> unavailableDifference) {
    return groupedLedgerFYs.entrySet().stream().map(ledgerFYListEntry -> updateLedgerFY(ledgerFYListEntry, availableDifference, unavailableDifference)).collect(toList());
  }

  private LedgerFY updateLedgerFY(Map.Entry<LedgerFY, Set<String>> ledgerFYListEntry, Map<String, MonetaryAmount> availableDifference, Map<String, MonetaryAmount> unavailableDifference) {
    LedgerFY ledgerFY = ledgerFYListEntry.getKey();

    MonetaryAmount availableAmount = ledgerFYListEntry.getValue().stream()
      .map(availableDifference::get).reduce(MonetaryFunctions::sum)
      .orElse(Money.zero(Monetary.getCurrency(ledgerFY.getCurrency())));

    MonetaryAmount unavailableAmount = ledgerFYListEntry.getValue().stream()
      .map(unavailableDifference::get).reduce(MonetaryFunctions::sum)
      .orElse(Money.zero(Monetary.getCurrency(ledgerFY.getCurrency())));

    double newAvailable = Math.max(Money.of(ledgerFY.getAvailable(), ledgerFY.getCurrency()).subtract(availableAmount).getNumber().doubleValue(), 0d);
    double newUnavailable = Math.max(Money.of(ledgerFY.getUnavailable(), ledgerFY.getCurrency()).subtract(unavailableAmount).getNumber().doubleValue(), 0d);

    return ledgerFY
      .withAvailable(newAvailable)
      .withUnavailable(newUnavailable);
  }

  private Future<Map<LedgerFY, Set<String>>> groupFundIdsByLedgerFy(Tx<List<Transaction>> tx) {
    Promise<Map<LedgerFY, Set<String>>> promise = Promise.promise();
    String sql = getLedgerFYsQuery();
    JsonArray params = new JsonArray();
    params.add(getSummaryId(tx.getEntity()
      .get(0)));
    tx.getPgClient()
      .select(tx.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          Map<LedgerFY, Set<String>> ledgers = reply.result()
            .getResults()
            .stream()
            .collect(ledgerFYFundIdsMapping());
          promise.complete(ledgers);
        }
      });
    return promise.future();
  }

  private Collector<JsonArray, ?, HashMap<LedgerFY, Set<String>>> ledgerFYFundIdsMapping() {
    int ledgerFYColumnNumber = 0;
    int fundIdColumnNumber = 1;
    return toMap(o -> new JsonObject(o.getString(ledgerFYColumnNumber)).mapTo(LedgerFY.class),
      o -> Collections.singleton(o.getString(fundIdColumnNumber)), (o, o2) -> {
        Set<String> newList = new HashSet<>(o);
        newList.addAll(o2);
        return newList;
      }, HashMap::new);
  }

  public String getLedgerFYsQuery() {
    return String.format(
        "SELECT ledger_fy.jsonb, funds.id FROM %s AS ledger_fy INNER JOIN %s AS funds"
            + " ON (funds.ledgerId = ledger_fy.ledgerId) INNER JOIN %s AS transactions"
            + " ON (funds.id = transactions.fromFundId)"
            + " WHERE (transactions.encumbrance_sourcePurchaseOrderId = ? AND ledger_fy.fiscalYearId = transactions.fiscalYearId);",
        getFullTableName(getTenantId(), LEDGERFY_TABLE), getFullTableName(getTenantId(), FUND_TABLE),
        getFullTemporaryTransactionTableName());
  }

}
