package org.folio.rest.core;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.folio.rest.util.ErrorCodes.GENERIC_ERROR_CODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import io.restassured.http.Header;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class RestClientTest {
  public static final Header X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, "restclienttest");
  public static final Header X_OKAPI_TOKEN = new Header(OKAPI_HEADER_TOKEN, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6Ijg3MTIyM2Q1LTYxZDEtNWRiZi1hYTcxLWVhNTcwOTc5MTQ1NSIsImlhdCI6MTU4NjUyMDA0NywidGVuYW50IjoiZGlrdSJ9._qlH5LDM_FaTH8MxIHKua-zsLmrBY7vpcJ-WrGupbHM");
  public static final Header X_OKAPI_USER_ID = new Header(OKAPI_USERID_HEADER, "d1d0a10b-c563-4c4b-ae22-e5a0c11623eb");

  @Mock
  private EventLoopContext ctxMock;
  @Mock
  private HttpClientInterface httpClient;

  private Map<String, String> okapiHeaders;
  private RequestContext requestContext;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
    okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + 8081);
    okapiHeaders.put(X_OKAPI_TOKEN.getName(), X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(X_OKAPI_TENANT.getName(), X_OKAPI_TENANT.getValue());
    okapiHeaders.put(X_OKAPI_USER_ID.getName(), X_OKAPI_USER_ID.getValue());
    requestContext = new RequestContext(ctxMock, okapiHeaders);
  }

  @Test
  void testPostShouldCreateEntity(VertxTestContext testContext) throws Exception {
    RestClient restClient = Mockito.spy(new RestClient("/orders/rollover"));

    String uuid = UUID.randomUUID().toString();
    LedgerFiscalYearRollover expTransaction = new LedgerFiscalYearRollover().withId(uuid);
    Response response = new Response();
    response.setBody(JsonObject.mapFrom(expTransaction));
    response.setCode(201);

    doReturn(httpClient).when(restClient).getHttpClient(okapiHeaders);
    doReturn(completedFuture(response)).when(httpClient)
      .request(eq(HttpMethod.POST), any(), eq("/orders/rollover"), eq(okapiHeaders));

    testContext.assertComplete(restClient.postEmptyResponse(expTransaction, requestContext))
      .onComplete(event -> testContext.completeNow());
  }

  @Test
  void testShouldThrowExceptionWhenCreatingEntity(VertxTestContext testContext) throws Exception {
    RestClient restClient = Mockito.spy(new RestClient("/orders/rollover"));

    Response response = new Response();
    response.setError(JsonObject.mapFrom(GENERIC_ERROR_CODE.toError()));
    response.setCode(400);

    String uuid = UUID.randomUUID().toString();
    LedgerFiscalYearRollover expTransaction = new LedgerFiscalYearRollover().withId(uuid);
    doReturn(httpClient).when(restClient).getHttpClient(okapiHeaders);
    doReturn(completedFuture(response)).when(httpClient)
            .request(eq(HttpMethod.POST), any(), eq("/orders/rollover"), eq(okapiHeaders));

    testContext.assertFailure(restClient.postEmptyResponse(expTransaction, requestContext))
    .onComplete(event -> testContext.completeNow());
  }

  @Test
  void testGetShouldCreateEntity(VertxTestContext testContext) throws Exception {
    RestClient restClient = Mockito.spy(new RestClient("/orders/rollover"));

    String uuid = UUID.randomUUID().toString();
    LedgerFiscalYearRollover expTransaction = new LedgerFiscalYearRollover().withId(uuid);
    Response response = new Response();
    response.setBody(JsonObject.mapFrom(expTransaction));
    response.setCode(201);

    doReturn(httpClient).when(restClient).getHttpClient(okapiHeaders);
    doReturn(completedFuture(response)).when(httpClient)
      .request(eq(HttpMethod.GET), eq("/orders/rollover?limit=1&offset=1&query=code%3D%27123%27"), eq(okapiHeaders));

    testContext.assertComplete(restClient.get("code='123'", 1, 1, requestContext, LedgerFiscalYearRollover.class))
      .onComplete(event -> testContext.completeNow());
  }

  @Test
  void testGetShouldThrowException(VertxTestContext testContext) throws Exception {
    RestClient restClient = Mockito.spy(new RestClient("/orders/rollover"));

    Response response = new Response();
    response.setError(JsonObject.mapFrom(GENERIC_ERROR_CODE.toError()));
    response.setCode(404);

    doReturn(httpClient).when(restClient).getHttpClient(okapiHeaders);
    doReturn(completedFuture(response)).when(httpClient)
      .request(eq(HttpMethod.GET), eq("/orders/rollover?limit=1&offset=1&query=code%3D%27123%27"), eq(okapiHeaders));

    testContext.assertFailure(restClient.get("code='123'", 1, 1, requestContext, LedgerFiscalYearRollover.class))
      .onComplete(event -> testContext.completeNow());
  }
}
