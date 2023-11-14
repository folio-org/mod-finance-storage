package org.folio.service.email;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.core.RestClientTest.X_OKAPI_TOKEN;
import static org.folio.rest.core.RestClientTest.X_OKAPI_USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.dao.ledger.LedgerDAO;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class EmailServiceTest {

  @InjectMocks
  private EmailService emailService;
  @Mock
  private RestClient restClient;
  @Mock
  private DBClientFactory dbClientFactory;
  @Mock
  private LedgerDAO ledgerDAO;

  private Vertx vertx;
  private RequestContext mockRequestContext;
  private static final String TEST_TENANT = "testtenant";
  private static final String OKAPI_TOKEN = "x-okapi-token";
  private static final String OKAPI_URL = "x-okapi-url";
  private static final String OKAPI_TENANT = "x-okapi-tenant";
  private static final String OKAPI_USER_ID = "x-okapi-user-id";

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
    vertx = Vertx.vertx();
    Context context = vertx.getOrCreateContext();
    Map<String, String> okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + NetworkUtils.nextFreePort());
    okapiHeaders.put(OKAPI_TOKEN, X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(OKAPI_TENANT, "restclienttest");
    okapiHeaders.put(OKAPI_USER_ID, X_OKAPI_USER_ID.getValue());
    mockRequestContext = new RequestContext(context, okapiHeaders);
  }

  @Test
  void shouldSendEmail(Vertx vertx) {
    when(restClient.get(anyString(), anyInt(), anyInt(), eq(mockRequestContext))).thenReturn(succeededFuture(getHostAddressJson()));
    when(restClient.getById(anyString(), eq(mockRequestContext))).thenReturn(succeededFuture(getUserJson()));
    when(ledgerDAO.getLedgerById(anyString(), any())).thenReturn(succeededFuture(new Ledger().withName("TestName")));
    when(dbClientFactory.getDbClient(mockRequestContext)).thenReturn(new DBClient(vertx, TEST_TENANT));

    emailService.createAndSendEmail(mockRequestContext, getRollover());
    verify(ledgerDAO, times(1)).getLedgerById(anyString(), any());
  }

  private LedgerFiscalYearRollover getRollover() {
    return new LedgerFiscalYearRollover().withLedgerId(UUID.randomUUID()
        .toString())
      .withRolloverType(LedgerFiscalYearRollover.RolloverType.PREVIEW)
      .withMetadata(new Metadata().withCreatedDate(new Date()));
  }

  private JsonObject getUserJson() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put("username", "testUserName");
    jsonObject.put("personal", new JsonObject(Collections.singletonMap("email", "email@example.org")));
    return jsonObject;
  }

  private JsonObject getHostAddressJson() {
    JsonObject jsonObject = new JsonObject();
    Map<String, String> config = Collections.singletonMap("value", "http://localhost:3030/");
    jsonObject.put("configs", List.of(config));
    return jsonObject;
  }
}
