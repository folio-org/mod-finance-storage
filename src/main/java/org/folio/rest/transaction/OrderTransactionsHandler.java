package org.folio.rest.transaction;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.impl.FinanceStorageAPI.LEDGERFY_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.impl.TransactionSummaryAPI.ORDER_TRANSACTION_SUMMARIES;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.MoneyUtils.subtractMoneyNonNegative;
import static org.folio.rest.persist.MoneyUtils.sumMoney;
import static org.folio.rest.util.ResponseUtils.handleFailure;

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
import org.folio.rest.persist.CriterionBuilder;
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


public class OrderTransactionsHandler extends AllOrNothingHandler {

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";

  public static final String GROUP_FUND_ID_BY_LEDGERFY = "SELECT ledger_fy.jsonb, funds.id FROM %s AS ledger_fy INNER JOIN %s AS funds"
    + " ON (funds.ledgerId = ledger_fy.ledgerId) INNER JOIN %s AS transactions"
    + " ON (funds.id = transactions.fromFundId)"
    + " WHERE (transactions.encumbrance_sourcePurchaseOrderId = ? AND ledger_fy.fiscalYearId = transactions.fiscalYearId);";

  public static final String SELECT_BUDGETS_BY_ORDER_ID = "SELECT DISTINCT ON (budgets.id) budgets.jsonb FROM %s AS budgets INNER JOIN %s AS transactions "
    + "ON transactions.fromFundId = budgets.fundId AND transactions.fiscalYearId = budgets.fiscalYearId "
    + "WHERE transactions.jsonb -> 'encumbrance' ->> 'sourcePurchaseOrderId' = ?";

  public static final String INSERT_PERMANENT_ENCUMBRANCES = "INSERT INTO %s (id, jsonb) SELECT id, jsonb FROM %s WHERE encumbrance_sourcePurchaseOrderId = ? "
    + "ON CONFLICT DO NOTHING;";

  public static final String INSERT_TEMPORARY_ENCUMBRANCES = "INSERT INTO %s (id, jsonb) VALUES (?, ?::JSON) "
    + "ON CONFLICT (lower(f_unaccent(jsonb ->> 'amount'::text)), lower(f_unaccent(jsonb ->> 'fromFundId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePurchaseOrderId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePoLineId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'initialAmountEncumbered'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'status'::text))) DO UPDATE SET id = excluded.id RETURNING id;";

  public OrderTransactionsHandler(Map<String, String> okapiHeaders, Context ctx, Handler<AsyncResult<Response>> asyncResultHandler) {
    super(TEMPORARY_ORDER_TRANSACTIONS, ORDER_TRANSACTION_SUMMARIES, okapiHeaders, ctx, asyncResultHandler);
  }

  @Override
  String getSummaryId(Transaction transaction) {
    return Optional.ofNullable(transaction.getEncumbrance())
      .map(Encumbrance::getSourcePurchaseOrderId)
      .orElse(null);
  }

  @Override
  Criterion getTransactionBySummaryIdCriterion(String value) {
    return new CriterionBuilder().with("encumbrance_sourcePurchaseOrderId", value).build();
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
    return String.format(INSERT_TEMPORARY_ENCUMBRANCES, getFullTemporaryTransactionTableName());
  }

  @Override
  String createPermanentTransactionsQuery() {
    return createPermanentTransactionsQuery(INSERT_PERMANENT_ENCUMBRANCES);
  }

  @Override
  protected String getSelectBudgetsQuery() {
    return getSelectBudgetsQuery(SELECT_BUDGETS_BY_ORDER_ID);
  }

  /**
   * To prevent partial encumbrance transactions for an order, all the encumbrances must be created following All or nothing
   */
  @Override
  Future<Tx<List<Transaction>>> processTemporaryToPermanentTransactions(Tx<List<Transaction>> tx) {
    return createPermanentTransactions(tx)
      .compose(updated -> {
        if (updated > 0) {
          return updateBudgetsLedgersTotals(tx);
        }
        return Future.succeededFuture(tx);
      });
  }

  private Future<Tx<List<Transaction>>> updateBudgetsLedgersTotals(Tx<List<Transaction>> tx) {
    return getBudgets(tx)
      .compose(oldBudgets -> {
        List<Budget> newBudgets = updateBudgetsTotals(tx.getEntity(), oldBudgets);
        return updateBudgets(tx, newBudgets)
          .compose(listTx -> updateLedgerFYsWithTotals(listTx, oldBudgets, newBudgets));
      });
  }

  private List<Budget> updateBudgetsTotals(List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<Budget, List<Transaction>> tempGrouped = groupTransactionsByBudget(tempTransactions, budgets);
    return tempGrouped.entrySet().stream()
      .map(this::updateBudgetTotals)
      .collect(toList());
  }

  private Map<Budget, List<Transaction>> groupTransactionsByBudget(List<Transaction> existingTransactions, List<Budget> budgets) {
    MultiKeyMap<String, Budget> groupedBudgets = new MultiKeyMap<>();

    groupedBudgets.putAll(budgets.stream().collect(toMap(budget -> new MultiKey<>(budget.getFundId(), budget.getFiscalYearId()), Function.identity())));

    return existingTransactions.stream()
      .collect(groupingBy(
        transaction -> groupedBudgets.get(transaction.getFromFundId(), transaction.getFiscalYearId())));

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

  private void recalculateAvailableUnavailable(Budget budget, CurrencyUnit currency) {
    double newUnavailable = sumMoney(currency, budget.getEncumbered(), budget.getAwaitingPayment(), budget.getExpenditures(),
      -budget.getOverEncumbrance(), -budget.getOverExpended());
    double newAvailable = subtractMoneyNonNegative(budget.getAllocated(), newUnavailable, currency);

    budget.setAvailable(newAvailable);
    budget.setUnavailable(newUnavailable);
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
    return String.format(GROUP_FUND_ID_BY_LEDGERFY, getFullTableName(getTenantId(), LEDGERFY_TABLE),
        getFullTableName(getTenantId(), FUND_TABLE), getFullTemporaryTransactionTableName());
  }

}
