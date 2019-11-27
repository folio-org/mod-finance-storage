package org.folio.rest.persist;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static org.folio.rest.persist.PgUtil.response;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.money.CurrencyUnit;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.javamoney.moneta.Money;

public final class HelperUtils {
  private HelperUtils() { }

  public static final String ID_FIELD_NAME = "id";

  public static String getEndpoint(Class<?> clazz) {
    return clazz.getAnnotation(Path.class).value();
  }

  public static <T> Future<Tx<T>> startTx(Tx<T> tx) {
    Promise<Tx<T>> promise = Promise.promise();

    tx.getPgClient().startTx(sqlConnection -> {
      tx.setConnection(sqlConnection);
      promise.complete(tx);
    });
    return promise.future();
  }

  public static <T> Future<Tx<T>> endTx(Tx<T> tx) {
    Promise<Tx<T>> promise = Promise.promise();
    tx.getPgClient().endTx(tx.getConnection(), v -> promise.complete(tx));
    return promise.future();
  }

  public static Future<Tx<String>> deleteRecordById(Tx<String> tx, String table) {
    Promise<Tx<String>> promise = Promise.promise();

    tx.getPgClient().delete(tx.getConnection(), table, tx.getEntity(), reply -> {
      if(reply.failed()) {
        HelperUtils.handleFailure(promise, reply);
      } else if (reply.result().getUpdated() == 0) {
        promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode()));
      } else {
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  public static void handleFailure(Promise promise, AsyncResult reply) {
    Throwable cause = reply.cause();
    String badRequestMessage = PgExceptionUtil.badRequestMessage(cause);
    if (badRequestMessage != null) {
      promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), badRequestMessage));
    } else {
      promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), cause.getMessage()));
    }
  }

  public static Future<Void> rollbackTransaction(Tx<?> tx) {
    Promise<Void> promise = Promise.promise();
    if (tx.getConnection().failed()) {
      promise.fail(tx.getConnection().cause());
    } else {
      tx.getPgClient().rollbackTx(tx.getConnection(), promise);
    }
    return promise.future();
  }

  public static void replyWithErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, HttpStatusException cause) {
    asyncResultHandler.handle(succeededFuture(Response.status(cause.getStatusCode())
      .entity(Optional.of(cause).map(HttpStatusException::getPayload).orElse(cause.getMessage()))
      .header(CONTENT_TYPE, MediaType.TEXT_PLAIN)
      .build()));
  }

  public static Criterion getCriterionByFieldNameAndValue(String fieldName, String operation, String fieldValue) {
    return new Criterion(getCriteriaByFieldNameAndValue(fieldName, operation, fieldValue));
  }

  public static Criterion getCriterionByFieldNameAndValueNotJsonb(String fieldName, String operation, String fieldValue) {
    return new Criterion(getCriteriaByFieldNameAndValueNotJsonb(fieldName, operation, fieldValue));
  }

  public static Criteria getCriteriaByFieldNameAndValue(String fieldName, String operation, String fieldValue) {
    Criteria a = new Criteria();
    a.addField("'" + fieldName + "'");
    a.setOperation(operation);
    a.setVal(fieldValue);
    return a;
  }

  public static Criteria getCriteriaByFieldNameAndValueNotJsonb(String fieldName, String operation, String fieldValue) {
    Criteria a = new Criteria();
    a.addField(fieldName);
    a.setOperation(operation);
    a.setVal(fieldValue);
    a.setJSONB(false);
    return a;
  }

  public static <T, E> void getEntitiesCollectionWithDistinctOn(EntitiesMetadataHolder<T, E> entitiesMetadataHolder, QueryHolder queryHolder, String sortField, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, Map<String, String> okapiHeaders) {
    Method respond500 = getRespond500(entitiesMetadataHolder, asyncResultHandler);
    Method respond400 = getRespond400(entitiesMetadataHolder, asyncResultHandler);
    try {
      PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
      postgresClient.get(queryHolder.getTable(), entitiesMetadataHolder.getClazz(), QueryHolder.JSONB, queryHolder.buildCQLQuery().toString(), true, false, false, null, sortField,
        reply -> processDbQueryReply(entitiesMetadataHolder, asyncResultHandler, respond500, respond400, reply));
    } catch (CQLQueryValidationException e) {

      asyncResultHandler.handle(response(e.getMessage(), respond400, respond500));
    } catch (Exception e) {

      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }


  private static Method getRespond500(EntitiesMetadataHolder entitiesMetadataHolder, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      return entitiesMetadataHolder.getRespond500WithTextPlainMethod();
    } catch (Exception e) {

      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return null;
    }
  }

  private static Method getRespond400(EntitiesMetadataHolder entitiesMetadataHolder, Handler<AsyncResult<Response>> asyncResultHandler) {
    try {
      return entitiesMetadataHolder.getRespond400WithTextPlainMethod();
    } catch (Exception e) {

      asyncResultHandler.handle(response(e.getMessage(), null, null));
      return null;
    }
  }

  private static <T, E> void processDbQueryReply(EntitiesMetadataHolder<T, E> entitiesMetadataHolder, Handler<AsyncResult<Response>> asyncResultHandler, Method respond500, Method respond400, AsyncResult<Results<T>> reply) {
    try {
      Method respond200 = entitiesMetadataHolder.getRespond200WithApplicationJson();
      if (reply.succeeded()) {
        E collection = entitiesMetadataHolder.getCollectionClazz().newInstance();
        List<T> results = reply.result().getResults();
        Method setResults =  entitiesMetadataHolder.getSetResultsMethod();
        Method setTotalRecordsMethod =  entitiesMetadataHolder.getSetTotalRecordsMethod();
        setResults.invoke(collection, results);
        Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
        setTotalRecordsMethod.invoke(collection, totalRecords);
        asyncResultHandler.handle(response(collection, respond200, respond500));
      } else {
        asyncResultHandler.handle(response(reply.cause().getLocalizedMessage(), respond400, respond500));
      }
    } catch (Exception e) {

      asyncResultHandler.handle(response(e.getMessage(), respond500, respond500));
    }
  }

  public static String getFullTableName(String tenantId, String tableName) {
    return PostgresClient.convertToPsqlStandard(tenantId) + "." + tableName;
  }

  public static Double subtractMoney(Double encumbered, Double subtrahend, CurrencyUnit currency) {
    return Money.of(encumbered, currency).subtract(Money.of(subtrahend, currency)).getNumber().doubleValue();
  }

  public static Double sumMoney(Double encumbered, Double amount, CurrencyUnit currency) {
    return Money.of(encumbered, currency).add(Money.of(amount, currency)).getNumber().doubleValue();
  }
}
