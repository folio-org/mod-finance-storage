package org.folio.service.email;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.core.RestClientTest.X_OKAPI_TOKEN;
import static org.folio.rest.core.RestClientTest.X_OKAPI_USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.folio.dao.ledger.LedgerDAO;
import org.folio.models.EmailEntity;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.service.settings.CommonSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class EmailServiceTest {

  private AutoCloseable mockitoMocks;
  @InjectMocks
  private EmailService emailService;
  @Mock
  private CommonSettingsService commonSettingsService;
  @Mock
  private RestClient restClient;
  @Mock
  private DBConn conn;
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
  private static final String ROLLOVER_EMAIL_FROM = "finance@library.org";

  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    vertx = Vertx.vertx();
    Context context = vertx.getOrCreateContext();
    Map<String, String> okapiHeaders = new HashMap<>();
    okapiHeaders.put(OKAPI_URL, "http://localhost:" + NetworkUtils.nextFreePort());
    okapiHeaders.put(OKAPI_TOKEN, X_OKAPI_TOKEN.getValue());
    okapiHeaders.put(OKAPI_TENANT, "restclienttest");
    okapiHeaders.put(OKAPI_USER_ID, X_OKAPI_USER_ID.getValue());
    mockRequestContext = new RequestContext(context, okapiHeaders);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void shouldSendEmail(Vertx vertx) {
    when(commonSettingsService.getHostAddress(mockRequestContext)).thenReturn(succeededFuture("http://localhost:3030/"));
    mockRolloverEmailFromSetting(succeededFuture(settingResults(ROLLOVER_EMAIL_FROM)));
    when(restClient.getById(anyString(), eq(mockRequestContext))).thenReturn(succeededFuture(getUserJson()));
    when(ledgerDAO.getLedgerById(anyString(), any())).thenReturn(succeededFuture(new Ledger().withName("TestName")));
    when(dbClientFactory.getDbClient(mockRequestContext)).thenReturn(new DBClient(vertx, TEST_TENANT));

    emailService.createAndSendEmail(mockRequestContext, getRollover(), conn);
    verify(conn, times(1)).get(eq("settings"), eq(Setting.class), any(), anyBoolean());
    verify(ledgerDAO, times(1)).getLedgerById(anyString(), any());
  }

  @Test
  void shouldResolveRolloverEmailFromSetting() {
    mockRolloverEmailFromSetting(succeededFuture(settingResults(ROLLOVER_EMAIL_FROM)));

    assertEquals(ROLLOVER_EMAIL_FROM, emailService.getRolloverEmailFrom(conn).result());
  }

  @Test
  void shouldResolveNullWhenRolloverEmailFromSettingIsAbsent() {
    mockRolloverEmailFromSetting(succeededFuture(settingResults()));

    assertNull(emailService.getRolloverEmailFrom(conn).result());
  }

  @Test
  void shouldSetFromOnEmailEntityWhenConfigured() {
    EmailEntity emailEntity = emailService.getEmailEntity(getRollover(), "http://localhost:3030/", "TestName", getUserJson(), ROLLOVER_EMAIL_FROM);

    assertEquals(ROLLOVER_EMAIL_FROM, emailEntity.getFrom());
    assertEquals(ROLLOVER_EMAIL_FROM, JsonObject.mapFrom(emailEntity).getString("from"));
    assertEquals("email@example.org", emailEntity.getTo());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldNotSetFromOnEmailEntityWhenNotConfigured(String from) {
    EmailEntity emailEntity = emailService.getEmailEntity(getRollover(), "http://localhost:3030/", "TestName", getUserJson(), from);

    assertNull(emailEntity.getFrom());
    assertFalse(JsonObject.mapFrom(emailEntity).getValue("from") instanceof String);
  }

  private void mockRolloverEmailFromSetting(Future<Results<Setting>> result) {
    when(conn.get(eq("settings"), eq(Setting.class), any(), anyBoolean())).thenReturn(result);
  }

  private static Results<Setting> settingResults(String... values) {
    Results<Setting> results = new Results<>();
    results.setResults(Arrays.stream(values)
      .map(value -> new Setting().withKey(EmailService.ROLLOVER_EMAIL_FROM_KEY).withValue(value))
      .toList());
    return results;
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

}
