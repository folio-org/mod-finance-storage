package org.folio.builders.error;

import static org.folio.rest.util.ErrorCodes.GENERIC_ERROR_CODE;

import java.text.MessageFormat;
import java.util.Optional;

import javax.ws.rs.core.Response;

import org.folio.rest.exception.HttpException;
import io.vertx.pgclient.PgException;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgExceptionUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.json.JsonObject;
import org.folio.rest.util.ErrorCodes;

public class NameCodeConstraintErrorBuilder {

  public  <T> HttpException buildException(AsyncResult<T> reply, Class<?> clazz ) {
    Throwable cause = reply.cause();
    if (cause instanceof PgException pgEx && PgExceptionUtil.isVersionConflict(pgEx)) {
      String message = MessageFormat.format(ErrorCodes.CONFLICT.getDescription(), pgEx.getTable(), pgEx.getErrorMessage());
      return new HttpException(Response.Status.CONFLICT.getStatusCode(), message);
    }
    String error = convertExceptionToStringError(reply, clazz);
    if (GENERIC_ERROR_CODE.getCode().equals(error)) {
      return new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), error);
    }
    return new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), error);
  }

  private <T> String convertExceptionToStringError(AsyncResult<T> reply, Class<?> clazz) {
    String msg = PgExceptionUtil.badRequestMessage(reply.cause());
    return Optional.ofNullable(msg)
                   .map(HelperUtils::getSQLUniqueConstraintName)
                   .map(uniqueConstraintName -> buildFieldConstraintError(uniqueConstraintName, clazz))
                   .orElse(GENERIC_ERROR_CODE.getCode());
  }

  private String buildFieldConstraintError(String uniqueConstraintName, Class<?> clazz)  {
    final String FIELD_CODE = "Code";
    final String FIELD_NAME = "Name";
    if (uniqueConstraintName.contains(FIELD_CODE.toLowerCase())) {
      return JsonObject.mapFrom(HelperUtils.buildFieldConstraintError(clazz.getSimpleName(), FIELD_CODE)).encode();
    } else if (uniqueConstraintName.contains(FIELD_NAME.toLowerCase())) {
      return JsonObject.mapFrom(HelperUtils.buildFieldConstraintError(clazz.getSimpleName(), FIELD_NAME)).encode();
    }
    return JsonObject.mapFrom(GENERIC_ERROR_CODE.toError()).encode();
  }
}
