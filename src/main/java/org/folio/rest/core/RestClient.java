package org.folio.rest.core;

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
import io.vertx.core.http.HttpResponseExpectation;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

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

  public Future<JsonObject> get(String path, RequestContext requestContext) {
    return get(requestContext, baseEndpoint + path);
  }

  private Future<JsonObject> get(RequestContext requestContext, String endpoint) {
    try {
      logger.debug("Calling GET {}", endpoint);
      String url = requestContext.getHeaders().get(RestConstants.OKAPI_URL) + endpoint;
      return webClient.getAbs(url)
        .putHeader(OKAPI_HEADER_TENANT, requestContext.getHeaders().get(OKAPI_HEADER_TENANT))
        .putHeader(OKAPI_HEADER_TOKEN, requestContext.getHeaders().get(OKAPI_HEADER_TOKEN))
        .send()
        .expecting(HttpResponseExpectation.SC_OK)
        .map(HttpResponse::bodyAsJsonObject)
        .onFailure(e -> logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, HttpMethod.GET, endpoint, e.getMessage()));
    } catch (Exception e) {
      logger.error(EXCEPTION_CALLING_ENDPOINT_MSG, HttpMethod.GET, endpoint, e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  public <T> Future<Void> postEmptyResponse(T entity, RequestContext requestContext) {
    try {
      JsonObject recordData = JsonObject.mapFrom(entity);
      return webClient.postAbs(requestContext.getHeaders().get(OKAPI_URL) + baseEndpoint)
        .putHeader(OKAPI_HEADER_TENANT, requestContext.getHeaders().get(OKAPI_HEADER_TENANT))
        .putHeader(OKAPI_HEADER_TOKEN, requestContext.getHeaders().get(OKAPI_HEADER_TOKEN))
        .sendJsonObject(recordData)
        .expecting(HttpResponseExpectation.SC_SUCCESS)
        .onFailure(e -> logger.error("'POST {}' request failed: {}", baseEndpoint, e.getCause()))
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
