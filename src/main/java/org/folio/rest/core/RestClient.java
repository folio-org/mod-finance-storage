package org.folio.rest.core;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
  private static final String SEARCH_ENDPOINT = "%s?limit=%s&offset=%s%s";
  private static final String EXCEPTION_CALLING_ENDPOINT_MSG = "Exception calling {} {} {}";

  private final String baseEndpoint;

  public RestClient(String baseEndpoint) {
    this.baseEndpoint = baseEndpoint;
  }

  public <T> Future<T> get(String query, int offset, int limit, RequestContext requestContext, Class<T> responseType) {
    String endpoint = String.format(SEARCH_ENDPOINT, baseEndpoint, limit, offset, buildQueryParam(query, LOGGER));
    return get(requestContext, endpoint, responseType);
  }

  public <T> Future<T> getById(String id, RequestContext requestContext, Class<T> responseType) {
    String endpoint = baseEndpoint + "/" + id;
    return get(requestContext, endpoint, responseType);
  }

  private <T> Future<T> get(RequestContext requestContext, String endpoint, Class<T> responseType) {
    Promise<T> promise = Promise.promise();
    HttpClientInterface client = getHttpClient(requestContext.getHeaders());
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Calling GET {}", endpoint);
    }
    try {
      client
        .request(HttpMethod.GET, endpoint, requestContext.getHeaders())
        .thenApply(response -> {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Validating response for GET {}", endpoint);
          }
          return verifyAndExtractBody(response);
        })
        .handle((body, t) -> {
          client.closeClient();
          if (t != null) {
            LOGGER.error(EXCEPTION_CALLING_ENDPOINT_MSG, t, HttpMethod.GET, endpoint);
            promise.fail(t.getCause());
          } else {
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug("The response body for GET {}: {}", endpoint, nonNull(body) ? body.encodePrettily() : null);
            }
            if (JsonObject.class.equals(responseType)) {
              promise.complete((T) body);
            } else {
              T responseEntity = body.mapTo(responseType);
              promise.complete(responseEntity);
            }
          }
          return null;
        });
    } catch (Exception e) {
      LOGGER.error(EXCEPTION_CALLING_ENDPOINT_MSG, e, HttpMethod.GET, baseEndpoint);
      client.closeClient();
      promise.fail(e);
    }
    return promise.future();
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

  private String encodeQuery(String query, Logger logger) {
    try {
      return URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      logger.error("Error happened while attempting to encode '{}'", e, query);
      throw new CompletionException(e);
    }
  }

  private String buildQueryParam(String query, Logger logger) {
    return isEmpty(query) ? EMPTY : "&query=" + encodeQuery(query, logger);
  }

  private JsonObject verifyAndExtractBody(Response response) {
    verifyResponse(response);
    return response.getBody();
  }
}
