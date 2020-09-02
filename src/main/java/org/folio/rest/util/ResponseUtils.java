package org.folio.rest.util;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import javax.ws.rs.core.Response;

import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.DBClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

import java.net.URI;
import java.net.URISyntaxException;

public class ResponseUtils {

  private static final Logger logger = LoggerFactory.getLogger(ResponseUtils.class);

  private ResponseUtils() {
  }

  public static Handler<AsyncResult<Void>> handleNoContentResponse(Handler<AsyncResult<Response>> asyncResultHandler, String id, DBClient client,
                                                                        String logMessage) {
    return result -> {
      if (result.failed()) {
        HttpStatusException cause = (HttpStatusException) result.cause();
        logger.error(logMessage, cause, id, "or associated data failed to be");

        // The result of rollback operation is not so important, main failure cause is used to build the response
        client.rollbackTransaction().onComplete(res -> asyncResultHandler.handle(buildErrorResponse(cause)));
      } else {
        logger.info(logMessage, id, "and associated data were successfully");
        asyncResultHandler.handle(buildNoContentResponse());
      }
    };
  }

  public static Handler<AsyncResult<Void>> handleNoContentResponse(Handler<AsyncResult<Response>> asyncResultHandler, String id,
                                                                        String logMessage) {
    return result -> {
      if (result.failed()) {
        HttpStatusException cause = (HttpStatusException) result.cause();
        logger.error(logMessage, cause, id, "or associated data failed to be");
        asyncResultHandler.handle(buildErrorResponse(cause));
      } else {
        logger.info(logMessage, id, "and associated data were successfully");
        asyncResultHandler.handle(buildNoContentResponse());
      }
    };
  }

  public static <T, V> void handleFailure(Promise<T> promise, AsyncResult<V> reply) {
    Throwable cause = reply.cause();
    String badRequestMessage = PgExceptionUtil.badRequestMessage(cause);
    if (badRequestMessage != null) {
      promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), badRequestMessage));
    } else {
      promise.fail(new HttpStatusException(INTERNAL_SERVER_ERROR.getStatusCode(), cause.getMessage()));
    }
  }

  public static Future<Response> buildNoContentResponse() {
    return Future.succeededFuture(Response.noContent().build());
  }

  public static Future<Response> buildErrorResponse(Throwable throwable) {
    final String message;
    final int code;

    if (throwable instanceof HttpStatusException) {
      code = ((HttpStatusException) throwable).getStatusCode();
      message = ((HttpStatusException) throwable).getPayload();
    } else {
      code = INTERNAL_SERVER_ERROR.getStatusCode();
      message = throwable.getMessage();
    }

    return Future.succeededFuture(buildErrorResponse(code, message));
  }

  private static Response buildErrorResponse(int code, String message) {
    return Response.status(code)
      .header(CONTENT_TYPE, code == 422 ? APPLICATION_JSON: TEXT_PLAIN)
      .entity(message)
      .build();
  }

  public static Response buildResponseWithLocation(String okapi, String endpoint, Object body) {
    try {
      return Response.created(new URI(okapi + endpoint))
        .header(CONTENT_TYPE, APPLICATION_JSON)
        .entity(body)
        .build();
    } catch (URISyntaxException e) {
      return Response.created(URI.create(endpoint))
        .header(CONTENT_TYPE, APPLICATION_JSON)
        .header(LOCATION, endpoint)
        .entity(body)
        .build();
    }
  }

  public static  <T> void handleVoidAsyncResult(Promise<Void> promise, AsyncResult<T> reply) {
    if(reply.failed()) {
      handleFailure(promise, reply);
    } else {
      promise.complete();
    }
  }
}
