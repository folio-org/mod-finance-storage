package org.folio.rest.util;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.HttpHeaders.LOCATION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;

import javax.ws.rs.core.Response;

import io.vertx.pgclient.PgException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.PgExceptionUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;

public class ResponseUtils {

  private static final Logger logger = LogManager.getLogger(ResponseUtils.class);

  private ResponseUtils() {
  }

  public static Future<Response> handleNoContentResponse(AsyncResult<Void> result, String id, String logMessage) {
    if (result.failed()) {
      HttpException cause = (HttpException) result.cause();
      logger.error(logMessage, cause, id, "or associated data failed to be");
      return buildErrorResponse(cause);
    } else {
      logger.info(logMessage, id, "and associated data were successfully");
      return buildNoContentResponse();
    }
  }

  public static Handler<AsyncResult<Void>> handleNoContentResponse(
      Handler<AsyncResult<Response>> asyncResultHandler, String id, String logMessage) {

    return result -> handleNoContentResponse(result, id, logMessage).onComplete(asyncResultHandler);
  }

  public static <T> Future<T> handleFailure(Throwable cause) {
    return Future.future(promise -> handleFailure(promise, Future.failedFuture(cause)));
  }

  public static <T, V> void handleFailure(Promise<T> promise, AsyncResult<V> reply) {
    Throwable cause = reply.cause();
    if (cause instanceof PgException && "23F09".equals(((PgException)cause).getCode())) {
      String message = MessageFormat.format(ErrorCodes.CONFLICT.getDescription(), ((PgException)cause).getTable(),
        ((PgException)cause).getErrorMessage());
      promise.fail(new HttpException(Response.Status.CONFLICT.getStatusCode(), message));
      return;
    }
    String badRequestMessage = PgExceptionUtil.badRequestMessage(cause);
    if (badRequestMessage != null) {
      promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), badRequestMessage));
    } else {
      logger.error(cause.getMessage());
      promise.fail(new HttpException(INTERNAL_SERVER_ERROR.getStatusCode(), cause.getMessage()));
    }
  }

  public static Future<Response> buildNoContentResponse() {
    return Future.succeededFuture(Response.noContent().build());
  }

  public static Future<Response> buildErrorResponse(Throwable throwable) {
    final String message;
    final int code;

    if (throwable instanceof HttpException) {
      code = ((HttpException) throwable).getStatusCode();
      message = ((HttpException) throwable).getPayload();
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
