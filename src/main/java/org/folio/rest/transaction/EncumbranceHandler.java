package org.folio.rest.transaction;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.rest.impl.TransactionSummaryAPI.ORDER_TRANSACTION_SUMMARIES;
import static org.folio.rest.persist.HelperUtils.getCriterionByFieldNameAndValueNotJsonb;
import static org.folio.rest.persist.HelperUtils.handleFailure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.core.Response;

import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class EncumbranceHandler extends AllOrNothingHandler {

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";

  public EncumbranceHandler(Map<String, String> okapiHeaders, Context ctx, Handler<AsyncResult<Response>> asyncResultHandler) {
    super(TEMPORARY_ORDER_TRANSACTIONS, ORDER_TRANSACTION_SUMMARIES, okapiHeaders, ctx, asyncResultHandler);
  }

  @Override
  public void updateTransaction(Transaction transaction) {
    verifyTransactionExistence(transaction.getId())
      .compose(aVoid -> processAllOrNothing(transaction, this::updatePermanentTransactions))
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

  private Future<Tx<List<Transaction>>> updatePermanentTransactions(Tx<List<Transaction>> tx) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();

    String sourcePurchaseOrderId = tx.getEntity()
      .get(0)
      .getEncumbrance()
      .getSourcePurchaseOrderId();

    String sql = "UPDATE " + getFullTransactionTableName() + " SET jsonb = (SELECT jsonb " + "FROM "
        + getTemporaryTransactionTable() + " WHERE " + getFullTransactionTableName() + ".id = "
        + getFullTemporaryTransactionTableName() + ".id) " + "WHERE id IN (SELECT id FROM " + getFullTemporaryTransactionTableName()
        + " WHERE encumbrance_sourcePurchaseOrderId = '" + sourcePurchaseOrderId + "');";

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
