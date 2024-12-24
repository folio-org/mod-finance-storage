package org.folio.rest.exception;

import static org.folio.rest.util.ErrorCodes.CONFLICT;
import static org.folio.rest.util.ErrorCodes.GENERIC_ERROR_CODE;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.util.ErrorCodes;

import java.util.Collections;
import java.util.List;

public class HttpException extends RuntimeException {
  private static final long serialVersionUID = 8109197948434861504L;

  private final int code;
  private final transient Errors errors;

  public HttpException(int code, String message) {
    super(StringUtils.isNotEmpty(message) ? message : ErrorCodes.GENERIC_ERROR_CODE.getDescription());
    this.code = code;
    var ec = code == 409 ? CONFLICT : GENERIC_ERROR_CODE;
    this.errors = new Errors()
      .withErrors(Collections.singletonList(new Error().withCode(ec.getCode()).withMessage(message)))
      .withTotalRecords(1);
  }

  public HttpException(int code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
    var ec = code == 409 ? CONFLICT : GENERIC_ERROR_CODE;
    Parameter causeParam = new Parameter().withKey("cause").withValue(cause.getMessage());
    Error error = new Error()
      .withCode(ec.getCode())
      .withMessage(message)
      .withParameters(List.of(causeParam));
    this.errors = new Errors()
      .withErrors(List.of(error))
      .withTotalRecords(1);
  }

  public HttpException(int code, Throwable cause) {
    super(cause.getMessage(), cause);
    this.code = code;
    var ec = code == 409 ? CONFLICT : GENERIC_ERROR_CODE;
    this.errors = new Errors()
      .withErrors(List.of(new Error().withCode(ec.getCode()).withMessage(cause.getMessage())))
      .withTotalRecords(1);
  }

  public HttpException(int code, Error error) {
    super(error.getMessage());
    this.code = code;
    this.errors = new Errors()
      .withErrors(List.of(error))
      .withTotalRecords(1);
  }

  public int getCode() {
    return code;
  }

  public Errors getErrors() {
    return errors;
  }
}
