package org.folio.rest.persist;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import java.util.Optional;

import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.vertx.core.Future;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public final class HelperUtils {
  private HelperUtils() { }

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

  public static Criterion getCriterionByFieldNameAndValue(String filedName, String operation, String fieldValue) {
    return new Criterion(getCriteriaByFieldNameAndValue(filedName, operation, fieldValue));
  }

  public static Criteria getCriteriaByFieldNameAndValue(String filedName, String operation, String fieldValue) {
    Criteria a = new Criteria();
    a.addField("'" + filedName + "'");
    a.setOperation(operation);
    a.setVal(fieldValue);
    return a;
  }
}
