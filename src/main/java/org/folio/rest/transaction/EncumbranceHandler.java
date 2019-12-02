package org.folio.rest.transaction;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.TransactionSummaryAPI.ORDER_TRANSACTION_SUMMARIES;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.handleFailure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.ws.rs.core.Response;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

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

  private List<Error> buildNullValidationError(String value, String key) {
    if (value == null) {
      Parameter parameter = new Parameter().withKey(key)
        .withValue("null");
      Error error = new Error().withCode("-1")
        .withMessage("may not be null")
        .withParameters(Collections.singletonList(parameter));
      return Collections.singletonList(error);
    }
    return Collections.emptyList();
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

  private Future<Tx<List<Transaction>>> updateBudgets(Tx<List<Transaction>> tx, List<Budget> budgets) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    List<JsonObject> jsonBudgets = budgets.stream().map(JsonObject::mapFrom).collect(Collectors.toList());
    String sql = "UPDATE " + getFullTableName(getTenantId(), BUDGET_TABLE) + " AS budgets " +
      "SET jsonb = b.jsonb FROM (VALUES  " + getValues(jsonBudgets) + ") AS b (id, jsonb) " +
      "WHERE b.id::uuid = budgets.id;";
    tx.getPgClient().execute(tx.getConnection(), sql, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  private String getValues(List<JsonObject> entities) {
    return entities.stream().map(entity -> "('" + entity.getString("id") + "', '" + entity.encode() + "'::json)").collect(Collectors.joining(","));
  }

  private Future<List<Budget>> getBudgets(Tx<List<Transaction>> tx) {
    Promise<List<Budget>> promise = Promise.promise();
    String sql = "SELECT DISTINCT ON (budgets.id) budgets.jsonb " +
      "FROM " + getFullTableName(getTenantId(), BUDGET_TABLE) + " AS budgets " +
      "INNER JOIN "+ getFullTemporaryTransactionTableName() + " AS transactions " +
      "ON transactions.fromFundId = budgets.fundId AND transactions.fiscalYearId = budgets.fiscalYearId " +
      "WHERE transactions.jsonb -> 'encumbrance' ->> 'sourcePurchaseOrderId' = ?";
    JsonArray params = new JsonArray();
    params.add(getSummaryId(tx.getEntity().get(0)));
    tx.getPgClient().select(tx.getConnection(), sql, params, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        List<Budget> budgets = reply.result().getResults()
          .stream().flatMap(JsonArray::stream)
          .map(o -> new JsonObject(o.toString())
            .mapTo(Budget.class)).collect(Collectors.toList());
        promise.complete(budgets);
      }
    });
    return promise.future();
  }

  private List<Budget> updateBudgetsTotals(List<Transaction> existingTransactions, List<Transaction> tempTransactions, List<Budget> budgets) {
    Map<String, Transaction> existingGrouped = existingTransactions.stream().collect(toMap(Transaction::getId, Function.identity()));
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
            if (isEncumbranceReleased(tmpTransaction)) {
              releaseEncumbrance(budget, currency, tmpTransaction);
            } else {
              double newEncumbered = HelperUtils.subtractMoney(budget.getEncumbered(), existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
              newEncumbered = HelperUtils.sumMoney(newEncumbered, tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
              budget.setEncumbered(newEncumbered);
            }
            double newAwaitingPayment = HelperUtils.sumMoney(budget.getAwaitingPayment(), existingTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
            newAwaitingPayment = HelperUtils.subtractMoney(newAwaitingPayment, tmpTransaction.getEncumbrance().getAmountAwaitingPayment(), currency);
            budget.setAwaitingPayment(newAwaitingPayment);
          }
        });
    }
    return budget;
  }

  private void releaseEncumbrance(Budget budget, CurrencyUnit currency, Transaction tmpTransaction) {
    budget.setAvailable(HelperUtils.sumMoney(budget.getAvailable(), tmpTransaction.getAmount(), currency));
    double newUnavailable = HelperUtils.subtractMoney(budget.getUnavailable(), tmpTransaction.getAmount(), currency);
    budget.setUnavailable(newUnavailable < 0 ? 0 : newUnavailable);
    budget.setEncumbered(HelperUtils.subtractMoney(budget.getEncumbered(), tmpTransaction.getAmount(), currency));
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

  private Future<Tx<List<Transaction>>> updatePermanentTransactions(Tx<List<Transaction>> tx) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    List<JsonObject> transactions = (tx.getEntity().stream().map(JsonObject::mapFrom).collect(Collectors.toList()));

    String sql = "UPDATE " + getFullTransactionTableName() + " AS transactions " +
      "SET jsonb = t.jsonb FROM (VALUES  "+ getValues(transactions) +") AS t (id, jsonb) " +
      "WHERE t.id::uuid = transactions.id;";
    tx.getPgClient()
      .execute(tx.getConnection(), sql, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(tx);
        }
      });
    return promise.future();
  }

  private Future<List<Transaction>> getPermanentTransactions(String summaryId) {
    Promise<List<Transaction>> promise = Promise.promise();

    Criterion criterion = new Criterion(getCriteriaByFieldNameAndValue("encumbrance", "=", summaryId).addField("'sourcePurchaseOrderId'"));

    getPostgresClient().get(TRANSACTION_TABLE, Transaction.class, criterion, true, false, reply -> {
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

}
