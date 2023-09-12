package org.folio.rest.core;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.WebClientFactory;
import org.folio.rest.RestConstants;
import org.folio.rest.core.model.RequestContext;
import org.folio.util.PercentCodec;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;

public class RestClient {

  private static final Logger logger = LogManager.getLogger(RestClient.class);

  private static final String SEARCH_ENDPOINT = "%s?limit=%s&offset=%s%s";
  private static final String EXCEPTION_CALLING_ENDPOINT_MSG = "Exception calling {} {} {}";
  private final String baseEndpoint;
  private final Vertx vertx = Vertx.currentContext() == null ? Vertx.vertx() : Vertx.currentContext().owner();
  private final WebClient webClient = WebClientFactory.getWebClient(vertx);

  public RestClient(String baseEndpoint) {
    this.baseEndpoint = baseEndpoint;
  }

  public Future<JsonObject> get(String query, int offset, int limit, RequestContext requestContext) {
    return get(requestContext, String.format(SEARCH_ENDPOINT, baseEndpoint, limit, offset, buildQueryParam(query)));
  }

  public Future<JsonObject> getById(String id, RequestContext requestContext) {
    return get(requestContext, baseEndpoint + "/" + id);
  }

  private Future<JsonObject> get(RequestContext requestContext, String endpoint) {
    try {
      logger.debug("Calling GET {}", endpoint);
      String url = requestContext.getHeaders().get(RestConstants.OKAPI_URL) + endpoint;
      return webClient.getAbs(url)
          .putHeader(OKAPI_HEADER_TENANT, requestContext.getHeaders().get(OKAPI_HEADER_TENANT))
          .putHeader(OKAPI_HEADER_TOKEN, requestContext.getHeaders().get(OKAPI_HEADER_TOKEN))
          .expect(ResponsePredicate.SC_OK)
          .send()
          .map(HttpResponse::bodyAsJsonObject)
          .onSuccess(body -> {
            if (logger.isDebugEnabled()) {
              logger.debug("The response body for GET {}: {}", endpoint, nonNull(body) ? body.encodePrettily() : null);
            }
          })
          .onFailure(e -> logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, HttpMethod.GET, endpoint, e.getMessage()));
    } catch (Exception e) {
      logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, HttpMethod.GET, endpoint, e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  public <T> Future<Void> postEmptyResponse(T entity, RequestContext requestContext) {
    try {
      JsonObject recordData = JsonObject.mapFrom(entity);

      if (logger.isDebugEnabled()) {
        logger.debug("Sending 'POST {}' with body: {}", baseEndpoint, recordData.encodePrettily());
      }

      return webClient.postAbs(requestContext.getHeaders().get(OKAPI_URL) + baseEndpoint)
          .putHeader(OKAPI_HEADER_TENANT, requestContext.getHeaders().get(OKAPI_HEADER_TENANT))
          .putHeader(OKAPI_HEADER_TOKEN, requestContext.getHeaders().get(OKAPI_HEADER_TOKEN))
          .expect(ResponsePredicate.status(200, 299))
          .sendJsonObject(recordData)
          .onSuccess(body -> logger.info(
              "'POST {}' request successfully processed. Record with '{}' id has been created", baseEndpoint, body))
          .onFailure(e -> logger.error("'POST {}' request failed: {}. Request body: {}",
              baseEndpoint, e.getCause(), recordData.encodePrettily()))
          .mapEmpty();
    } catch (Exception e) {
      logger.error("'POST {}' request failed: {}.", baseEndpoint, e.getCause());
      return Future.failedFuture(e);
    }
  }

  private String buildQueryParam(String query) {
    return isEmpty(query) ? EMPTY : "&query=" + PercentCodec.encode(query);
  }
}
