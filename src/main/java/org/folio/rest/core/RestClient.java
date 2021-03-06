package org.folio.rest.core;


import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestConstants;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.util.ErrorCodes;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

public class RestClient {
  private static final Logger LOGGER = LogManager.getLogger(RestClient.class);
  private static final String ERROR_MESSAGE = "errorMessage";

  private final String baseEndpoint;

  public RestClient(String baseEndpoint) {
    this.baseEndpoint = baseEndpoint;
  }

  public <T> Future<Void> postEmptyResponse(T entity, RequestContext requestContext) {
    Promise<Void> promise = Promise.promise();
    String endpoint = baseEndpoint;
    JsonObject recordData = JsonObject.mapFrom(entity);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Sending 'POST {}' with body: {}", endpoint, recordData.encodePrettily());
    }

    HttpClientInterface client = getHttpClient(requestContext.getHeaders());
    try {
      client.request(HttpMethod.POST, recordData.toBuffer(), endpoint, requestContext.getHeaders())
        .thenAccept(this::verifyResponse)
        .handle((body, t) -> {
          client.closeClient();
          if (t != null) {
            LOGGER.error("'POST {}' request failed. Request body: {}", endpoint, recordData.encodePrettily(), t.getCause());
            promise.fail(t.getCause());
          } else {
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug("'POST {}' request successfully processed. Record with '{}' id has been created", endpoint, body);
            }
            promise.complete();
          }
          return null;
        });
    } catch (Exception e) {
      LOGGER.error("'POST {}' request failed. Request body: {}", endpoint, recordData.encodePrettily(), e);
      client.closeClient();
      promise.fail(e);
    }

    return promise.future();
  }

  public HttpClientInterface getHttpClient(Map<String, String> okapiHeaders) {
    final String okapiURL = okapiHeaders.getOrDefault(RestConstants.OKAPI_URL, "");
    final String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(OKAPI_HEADER_TENANT));

    return HttpClientFactory.getHttpClient(okapiURL, tenantId);

  }

  private void verifyResponse(Response response) {
    if (!Response.isSuccess(response.getCode())) {
      String errorMsg = response.getError()
        .getString(ERROR_MESSAGE);
      HttpException httpException = getErrorByCode(errorMsg).map(errorCode -> new HttpException(response.getCode(), errorCode))
        .orElse(new HttpException(response.getCode(), errorMsg));
      throw new CompletionException(httpException);
    }
  }

  private Optional<ErrorCodes> getErrorByCode(String errorCode) {
    return EnumSet.allOf(ErrorCodes.class)
      .stream()
      .filter(errorCodes -> errorCodes.getCode()
        .equals(errorCode))
      .findAny();
  }

}
