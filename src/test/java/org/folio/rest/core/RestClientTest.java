package org.folio.rest.core;

import static org.folio.rest.RestConstants.OKAPI_URL;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover.RolloverType;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.restassured.http.Header;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class RestClientTest {
  public static final Header X_OKAPI_TENANT = new Header(OKAPI_HEADER_TENANT, "restclienttest");
  public static final Header X_OKAPI_TOKEN = new Header(OKAPI_HEADER_TOKEN, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6Ijg3MTIyM2Q1LTYxZDEtNWRiZi1hYTcxLWVhNTcwOTc5MTQ1NSIsImlhdCI6MTU4NjUyMDA0NywidGVuYW50IjoiZGlrdSJ9._qlH5LDM_FaTH8MxIHKua-zsLmrBY7vpcJ-WrGupbHM");
  public static final Header X_OKAPI_USER_ID = new Header(OKAPI_USERID_HEADER, "d1d0a10b-c563-4c4b-ae22-e5a0c11623eb");

  @Mock
  private EventLoopContext ctxMock;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testPostShouldCreateEntity(Vertx vertx, VertxTestContext testContext) {
    var hostCheckpoint = testContext.checkpoint();
    var clientCheckpoint = testContext.checkpoint();
    vertx.createHttpServer()
    .requestHandler(request -> testContext.verify(() -> {
      assertThat(request.method(), is(HttpMethod.POST));
      assertThat(request.path(), is("/orders"));
      assertThat(request.getHeader(OKAPI_HEADER_TENANT), is("cat"));
      assertThat(request.getHeader(OKAPI_HEADER_TOKEN), is("manekineko"));
      request.response().setStatusCode(201);
      request.response().end();
      request.bodyHandler(body -> testContext.verify(() -> {
        assertThat(body.toJsonObject().getString("rolloverType"), is("Preview"));
        hostCheckpoint.flag();
      }));
    }))
    .listen(0)
    .compose(host -> {
      var rollover = new LedgerFiscalYearRollover().withRolloverType(RolloverType.PREVIEW);
      return new RestClient("/orders").postEmptyResponse(rollover, requestContext(host, "cat", "manekineko"));
    }).onComplete(testContext.succeeding(x -> clientCheckpoint.flag()));
  }

  @Test
  void testPostShouldThrowException(Vertx vertx, VertxTestContext testContext) {
    vertx.createHttpServer()
    .requestHandler(request -> request.response().setStatusCode(500).end())
    .listen(0)
    .compose(host -> {
      var rollover = new LedgerFiscalYearRollover().withRolloverType(RolloverType.PREVIEW);
      return new RestClient("/orders").postEmptyResponse(rollover, requestContext(host, "cat", "manekineko"));
    }).onComplete(testContext.failingThenComplete());
  }

  @Test
  void testGetByQueryShouldCreateJson(Vertx vertx, VertxTestContext testContext) {
    vertx.createHttpServer()
    .requestHandler(request -> testContext.verify(() -> {
      assertThat(request.path(), is("/chunks"));
      assertThat(request.getParam("offset"), is("3"));
      assertThat(request.getParam("limit"), is("5"));
      assertThat(request.getHeader(OKAPI_HEADER_TENANT), is("dog"));
      assertThat(request.getHeader(OKAPI_HEADER_TOKEN), is("majatoken"));
      request.response().end("{ \"bit\":\"yummy\" }");
    }))
    .listen(0)
    .compose(host -> new RestClient("/chunks").get("code==X1", 3, 5, requestContext(host, "dog", "majatoken")))
    .onComplete(testContext.succeeding(jsonObject -> {
      assertThat(jsonObject.getString("bit"), is("yummy"));
      testContext.completeNow();
    }));
  }

  @Test
  void testGetByQueryShouldThrowException(VertxTestContext testContext) {
    new RestClient("/chunks").get("code==X1", 3, 5, requestContext(null, "dog", "majatoken"))
    .onComplete(testContext.failingThenComplete());
  }

  @Test
  void testGetByIdShouldCreateJson(Vertx vertx, VertxTestContext testContext) {
    var userId = UUID.randomUUID().toString();
    vertx.createHttpServer()
    .requestHandler(request -> testContext.verify(() -> {
      assertThat(request.path(), is("/users/" + userId));
      assertThat(request.getHeader(OKAPI_HEADER_TENANT), is("bee"));
      assertThat(request.getHeader(OKAPI_HEADER_TOKEN), is("janetoken"));
      request.response().end("{ \"username\":\"jane\" }");
    }))
    .listen(0)
    .compose(host -> new RestClient("/users").getById(userId, requestContext(host, "bee", "janetoken")))
    .onComplete(testContext.succeeding(jsonObject -> {
      assertThat(jsonObject.getString("username"), is("jane"));
      testContext.completeNow();
    }));
  }

  @Test
  void testGetByIdShouldThrowException(Vertx vertx, VertxTestContext testContext) {
    var userId = UUID.randomUUID().toString();
    vertx.createHttpServer()
    .requestHandler(request -> request.response().setStatusCode(400).end())
    .listen(0)
    .compose(httpServer -> new RestClient("/users").getById(userId, requestContext(httpServer, "bee", "janetoken")))
    .onComplete(testContext.failingThenComplete());
  }

  private RequestContext requestContext(HttpServer httpServer, String tenant, String token) {
    var port = httpServer == null ? NetworkUtils.nextFreePort() : httpServer.actualPort();
    var headers = new CaseInsensitiveMap<String,String>(Map.of(
        OKAPI_URL, "http://localhost:" + port,
        OKAPI_HEADER_TENANT, tenant,
        OKAPI_HEADER_TOKEN, token));
    return new RequestContext(ctxMock, headers);
  }
}
