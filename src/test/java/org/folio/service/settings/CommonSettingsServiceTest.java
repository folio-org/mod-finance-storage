package org.folio.service.settings;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.core.RestClientTest.X_OKAPI_TOKEN;
import static org.folio.rest.core.RestClientTest.X_OKAPI_USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith({ VertxExtension.class, MockitoExtension.class })
public class CommonSettingsServiceTest {

  private static final String OKAPI_TOKEN = "x-okapi-token";
  private static final String OKAPI_URL = "x-okapi-url";
  private static final String OKAPI_TENANT = "x-okapi-tenant";
  private static final String OKAPI_USER_ID = "x-okapi-user-id";
  private static final String TEST_HOST_ADDRESS = "http://localhost:3030/";

  private RequestContext mockRequestContext;
  @Mock
  private RestClient restClient;
  @InjectMocks
  private CommonSettingsService commonSettingsService;

  @BeforeEach
  public void initMocks() {
    Map<String, String> okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + NetworkUtils.nextFreePort());
    okapiHeaders.put(OKAPI_TOKEN, X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(OKAPI_TENANT, "restclienttest");
    okapiHeaders.put(OKAPI_USER_ID, X_OKAPI_USER_ID.getValue());
    mockRequestContext = new RequestContext(Vertx.vertx().getOrCreateContext(), okapiHeaders);
    commonSettingsService.init();
  }

  @Test
  void getHostAddress_returnsHostAddress_whenSuccessful(VertxTestContext testContext) {
    JsonObject responseJson = getHostAddressJson();
    when(restClient.get(eq(CommonSettingsService.HOST_ADDRESS_ENDPOINT), eq(mockRequestContext)))
      .thenReturn(succeededFuture(responseJson));

    testContext.assertComplete(commonSettingsService.getHostAddress(mockRequestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertTrue(event.succeeded());
          assertEquals(TEST_HOST_ADDRESS, event.result());
          verify(restClient, times(1)).get(eq(CommonSettingsService.HOST_ADDRESS_ENDPOINT), eq(mockRequestContext));
        });
        testContext.completeNow();
      });
  }

  @Test
  void getHostAddress_failsWhenRestClientFails(VertxTestContext testContext) {
    String errorMessage = "Failed to fetch host address";
    when(restClient.get(eq(CommonSettingsService.HOST_ADDRESS_ENDPOINT), eq(mockRequestContext)))
      .thenReturn(failedFuture(new RuntimeException(errorMessage)));

    testContext.assertFailure(commonSettingsService.getHostAddress(mockRequestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertTrue(event.failed());
          assertTrue(event.cause().getMessage().contains(errorMessage));
          verify(restClient, times(1)).get(eq(CommonSettingsService.HOST_ADDRESS_ENDPOINT), eq(mockRequestContext));
        });
        testContext.completeNow();
      });
  }

  @Test
  void getHostAddress_handlesNullResponse(VertxTestContext testContext) {
    when(restClient.get(eq(CommonSettingsService.HOST_ADDRESS_ENDPOINT), eq(mockRequestContext)))
      .thenReturn(succeededFuture(null));

    testContext.assertFailure(commonSettingsService.getHostAddress(mockRequestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertTrue(event.failed());
          verify(restClient, times(1)).get(eq(CommonSettingsService.HOST_ADDRESS_ENDPOINT), eq(mockRequestContext));
        });
        testContext.completeNow();
      });
  }

  private JsonObject getHostAddressJson() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put(CommonSettingsService.HOST_ADDRESS_VALUE_FIELD, TEST_HOST_ADDRESS);
    return jsonObject;
  }
}
