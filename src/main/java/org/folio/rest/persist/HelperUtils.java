package org.folio.rest.persist;

import static io.vertx.core.Future.succeededFuture;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

import java.util.Optional;

import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public final class HelperUtils {
  private HelperUtils() { }

  public static String getEndpoint(Class<?> clazz) {
    return clazz.getAnnotation(Path.class).value();
  }

  public static <T> Future<Tx<T>> startTx(Tx<T> tx) {
    Future<Tx<T>> future = Future.future();

    tx.getPgClient().startTx(sqlConnection -> {
      tx.setConnection(sqlConnection);
      future.complete(tx);
    });
    return future;
  }

  public static <T> Future<Tx<T>> endTx(Tx<T> tx) {
    Future<Tx<T>> future = Future.future();
    tx.getPgClient().endTx(tx.getConnection(), v -> future.complete(tx));
    return future;
  }

  public static Future<Tx<String>> deleteRecordById(Tx<String> tx, String table) {
    Future<Tx<String>> future = Future.future();

    tx.getPgClient().delete(tx.getConnection(), table, tx.getEntity(), reply -> {
      if(reply.failed()) {
        HelperUtils.handleFailure(future, reply);
      } else if (reply.result().getUpdated() == 0) {
        future.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode()));
      } else {
        future.complete(tx);
      }
    });
    return future;
  }

  public static void handleFailure(Future future, AsyncResult reply) {
    Throwable cause = reply.cause();
    String badRequestMessage = PgExceptionUtil.badRequestMessage(cause);
    if (badRequestMessage != null) {
      future.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), badRequestMessage));
    } else {
      future.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), cause.getMessage()));
    }
  }

  public static Future<Void> rollbackTransaction(Tx<?> tx) {
    Future<Void> future = Future.future();
    if (tx.getConnection().failed()) {
      future.fail(tx.getConnection().cause());
    } else {
      tx.getPgClient().rollbackTx(tx.getConnection(), future);
    }
    return future;
  }

  public static void replyWithErrorResponse(Handler<AsyncResult<Response>> asyncResultHandler, HttpStatusException cause) {
    asyncResultHandler.handle(succeededFuture(Response.status(cause.getStatusCode())
      .entity(Optional.of(cause).map(HttpStatusException::getPayload).orElse(cause.getMessage()))
      .header(CONTENT_TYPE, MediaType.TEXT_PLAIN)
      .build()));
  }

  public static Criterion getCriterionByFieldNameAndValue(String filedName, String operation, String fieldValue) {
    Criteria a = new Criteria();
    a.addField("'" + filedName + "'");
    a.setOperation(operation);
    a.setVal(fieldValue);
    return new Criterion(a);
  }
}
