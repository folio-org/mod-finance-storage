package org.folio.rest.transaction;

import static java.lang.Math.max;
import static java.lang.Math.min;
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

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    return "INSERT INTO " + getFullTemporaryTransactionTableName() + " (id, jsonb) VALUES (?, ?::JSON) "
        + "ON CONFLICT (lower(f_unaccent(jsonb ->> 'amount'::text)), " + "lower(f_unaccent(jsonb ->> 'fromFundId'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePurchaseOrderId'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePoLineId'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'initialAmountEncumbered'::text)), "
        + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'status'::text))) " + "DO UPDATE SET id = excluded.id RETURNING id;";
  }

  @Override
  String createPermanentTransactionsQuery() {
    return String.format("INSERT INTO %s (id, jsonb) " + "SELECT id, jsonb FROM %s WHERE encumbrance_sourcePurchaseOrderId = ? "
        + "ON CONFLICT DO NOTHING;", getFullTransactionTableName(), getTemporaryTransactionTable());
  }

  private Future<Tx<List<Transaction>>> processTransactionsUponUpdate(Tx<List<Transaction>> tx) {

    List<Transaction> tempTransactions = tx.getEntity();

    return getPermanentTransactions(getSummaryId(tempTransactions.get(0)))
      .compose(existingTransactions -> updateBudgetsTotals(tx, existingTransactions))
      .compose(this::updatePermanentTransactions);
  }

  private Future<Tx<List<Transaction>>> updateBudgetsTotals(Tx<List<Transaction>> tx, List<Transaction> existingTransactions) {
    return getBudgets(tx)
      .map(budgets -> updateBudgetsTotals(existingTransactions, tx.getEntity(), budgets))
      .compose(budgets -> updateBudgets(tx, budgets));
  }

  private Future<Tx<List<Transaction>>> updateBudgetsTotals(Tx<List<Transaction>> tx) {
    return getBudgets(tx)
      .map(budgets -> updateBudgetsTotals(tx.getEntity(), budgets))
      .compose(budgets -> updateBudgets(tx, budgets));
  }


  @Override
  protected String getBudgetsQuery() {
    return "SELECT DISTINCT ON (budgets.id) budgets.jsonb " +
    "FROM " + getFullTableName(getTenantId(), BUDGET_TABLE) + " AS budgets " +
    "INNER JOIN "+ getFullTemporaryTransactionTableName() + " AS transactions " +
    "ON transactions.fromFundId = budgets.fundId AND transactions.fiscalYearId = budgets.fiscalYearId " +
    "WHERE transactions.jsonb -> 'encumbrance' ->> 'sourcePurchaseOrderId' = ?";
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
              double newEncumbered = subtractMoney(budget.getEncumbered(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
              newEncumbered = sumMoney(newEncumbered, existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
              budget.setEncumbered(newEncumbered);
              double newAmount = subtractMoney(tmpTransaction.getEncumbrance().getInitialAmountEncumbered(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
              newAmount = subtractMoney(newAmount, tmpTransaction.getEncumbrance().getAmountExpended(), currency);
              tmpTransaction.setAmount(newAmount);
              double newAwaitingPayment = sumMoney(budget.getAwaitingPayment(), tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
              newAwaitingPayment = subtractMoneyNonNegative(newAwaitingPayment, existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
              budget.setAwaitingPayment(newAwaitingPayment);
            }
          }
        });
    }
    return budget;
  }

  private Budget updateBudgetTotals(Map.Entry<Budget, List<Transaction>> entry) {
    Budget budget = entry.getKey();
    if (isNotEmpty(entry.getValue())) {
      CurrencyUnit currency = Monetary.getCurrency(entry.getValue().get(0).getCurrency());
      entry.getValue()
        .forEach(tmpTransaction -> {
          double newEncumbered = sumMoney(budget.getEncumbered(), tmpTransaction.getAmount(), currency);
          budget.setEncumbered(newEncumbered);
          double newAvailable = subtractMoney(budget.getAvailable(), tmpTransaction.getAmount(), currency);
          budget.setAvailable(newAvailable);
          double newUnavailable = min(budget.getAllocated(), sumMoney(budget.getUnavailable(), tmpTransaction.getAmount(), currency));
          budget.setUnavailable(newUnavailable);
          double newOverEncumbrance = max(0, subtractMoney(subtractMoney(newEncumbered, newUnavailable, currency), newAvailable, currency));
          budget.setOverEncumbrance(newOverEncumbrance);
        });
    }
    return budget;
  }

  private void releaseEncumbrance(Budget budget, CurrencyUnit currency, Transaction tmpTransaction) {
    budget.setAvailable(sumMoney(budget.getAvailable(), tmpTransaction.getAmount(), currency));
    double newUnavailable = subtractMoney(budget.getUnavailable(), tmpTransaction.getAmount(), currency);
    budget.setUnavailable(newUnavailable < 0 ? 0 : newUnavailable);
    budget.setEncumbered(subtractMoney(budget.getEncumbered(), tmpTransaction.getAmount(), currency));
    tmpTransaction.setAmount(0d);
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
    return updateBudgetsTotals(tx)
      .compose(this::updateLedgerFYsTotals)
      .compose(this::createPermanentTransactions);
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
  private List<LedgerFY> calculateLedgerFyTotals(Map<LedgerFY, List<Transaction>> groupedLedgerFYs) {
    return groupedLedgerFYs.entrySet().stream().map(this::updateLedgerFY).collect(toList());
  }


  private String getLedgersQuery(){
    return "SELECT ledger_fy.jsonb, transactions.jsonb FROM " + getFullTableName(getTenantId(), LEDGERFY_TABLE)
      + " AS ledger_fy INNER JOIN " + getFullTableName(getTenantId(), FUND_TABLE) + " AS funds"
      + " ON (funds.ledgerId = ledger_fy.ledgerId)"
      + " INNER JOIN " + getFullTemporaryTransactionTableName() + " AS transactions"
      + " ON (funds.id = transactions.fromFundId)"
      + " WHERE (transactions.encumbrance_sourcePurchaseOrderId = ? AND ledger_fy.fiscalYearId = transactions.fiscalYearId);";
  }

  private Collector<JsonArray, ?, HashMap<LedgerFY, List<Transaction>>> ledgerFYTransactionsMapping() {
    int ledgerFYColumnNumber = 0;
    int transactionsColumnNumber = 1;
    return toMap(o -> new JsonObject(o.getString(ledgerFYColumnNumber))
      .mapTo(LedgerFY.class), o -> Collections.singletonList(new JsonObject(o.getString(transactionsColumnNumber))
      .mapTo(Transaction.class)), (o, o2) -> {
      List<Transaction> newList = new ArrayList<>(o); newList.addAll(o2); return newList;}, HashMap::new);
  }
  private Future<Tx<List<Transaction>>> updateLedgerFYs(Tx<List<Transaction>> tx, List<LedgerFY> ledgerFYs) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    if (ledgerFYs.isEmpty()) {
      promise.complete(tx);
    } else {
      List<JsonObject> jsonLedgerFYs = ledgerFYs.stream().map(JsonObject::mapFrom).collect(toList());
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


  private LedgerFY updateLedgerFY(Map.Entry<LedgerFY, List<Transaction>> ledgerFYListEntry) {
    LedgerFY ledgerFY = ledgerFYListEntry.getKey();
    MonetaryAmount totalAmount = getTotalTransactionsAmount(ledgerFYListEntry);
    double newAvailable = Math.max(Money.of(ledgerFY.getAvailable(), ledgerFY.getCurrency()).subtract(totalAmount).getNumber().doubleValue(), 0d);
    double newUnavailable = Math.max(Money.of(ledgerFY.getUnavailable(), ledgerFY.getCurrency()).add(totalAmount).getNumber().doubleValue(), 0d);

    return ledgerFY
      .withAvailable(newAvailable)
      .withUnavailable(newUnavailable);
  }
  private MonetaryAmount getTotalTransactionsAmount(Map.Entry<LedgerFY, List<Transaction>> ledgerFYListEntry) {
    return ledgerFYListEntry.getValue().stream()
      .map(transaction -> (MonetaryAmount) Money.of(transaction.getAmount(), transaction.getCurrency()))
      .reduce(MonetaryFunctions::sum).orElse(Money.zero(Monetary.getCurrency(ledgerFYListEntry.getKey().getCurrency())));
  }


}
