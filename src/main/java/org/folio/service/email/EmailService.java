package org.folio.service.email;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.ledger.LedgerDAO;
import org.folio.models.EmailEntity;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.utils.EmailOkapiClient;

import javax.ws.rs.core.MediaType;

public class EmailService {

  private static final String ACCEPT_KEY = "Accept";
  private static final String OKAPI_URL = "x-okapi-url";
  private static final String OKAPI_TENANT = "X-Okapi-Tenant";
  private static final String OKAPI_TOKEN = "x-okapi-token";
  private static final String OKAPI_USER_ID = "x-okapi-user-id";
  private static final String UTC_ZONE_ID = "UTC";
  private static final String FORMAT_PATTERN = "dd-MM-yyyy HH:mm:ss";
  private static final String CONFIGURATION_QUERY = "module==USERSBL and code==FOLIO_HOST";
  private static final String EMAIL_ENDPOINT = "/email";
  private static final String ROLLOVER_LOGS_ENDPOINT = "/finance/ledger/%s/rollover-logs";
  private static final String EMAIL_HEADER = "FOLIO ledger rollover";
  private static final String USERNAME_KEY = "username";
  private static final String PERSONAL_KEY = "personal";
  private static final String EMAIL_KEY = "email";
  private static final String CONFIGS_KEY = "configs";
  private static final String VALUE_KEY = "value";

  private static final Logger logger = LogManager.getLogger(EmailService.class);
  private static final String COMMIT_MESSAGE = "<p>Hi %s,<br><br>The results of your fiscal year rollover from %s " +
    "for %s are ready for review. Click <a href=\"%s\">here</a> to review the results.<br><br>FOLIO</p>";
  private static final String PREVIEW_MESSAGE = "<p>Hi %s,<br><br>The results of your fiscal year rollover test from %s " +
    "for %s are ready for review. Click <a href=\"%s\">here</a> to review the results.<br><br>FOLIO</p>";

  private final RestClient configurationRestClient;
  private final RestClient userRestClient;
  private final DBClientFactory dbClientFactory;
  private final LedgerDAO ledgerDAO;

  public EmailService(RestClient configurationRestClient, RestClient userRestClient, DBClientFactory dbClientFactory, LedgerDAO ledgerDAO) {
    this.configurationRestClient = configurationRestClient;
    this.userRestClient = userRestClient;
    this.dbClientFactory = dbClientFactory;
    this.ledgerDAO = ledgerDAO;
  }

  public void createAndSendEmail(RequestContext requestContext, LedgerFiscalYearRollover rollover) {
    getHostAddress(requestContext)
      .onSuccess(hostAddressResponse -> getCurrentUser(requestContext)
        .onSuccess(userResponse -> {
          DBClient client = dbClientFactory.getDbClient(requestContext);
          ledgerDAO.getLedgerById(rollover.getLedgerId(), client)
            .onSuccess(ledger -> {
              String hostAddress = hostAddressResponse.getJsonArray(CONFIGS_KEY).getJsonObject(0).getMap().get(VALUE_KEY).toString();
              String linkToRolloverLedger = createRolloverLedgerLink(hostAddress, rollover.getLedgerId());
              Map<String, String> headers = getHeaders(requestContext);
              EmailEntity emailEntity = getEmailEntity(rollover, linkToRolloverLedger, ledger.getName(), userResponse);

              sendEmail(requestContext, headers, emailEntity);
            }).onFailure(t -> logger.error("Getting ledger failed {}", t.getMessage()));
        }).onFailure(t -> logger.error("Getting user failed {}", t.getMessage())))
      .onFailure(t -> logger.error("Getting host address failed {}", t.getMessage()));
  }

  private void sendEmail(RequestContext requestContext, Map<String, String> headers, EmailEntity emailEntity) {
    EmailOkapiClient emailOkapiClient = new EmailOkapiClient(requestContext.getHeaders().get(OKAPI_URL), requestContext.getContext().owner(), headers);
    emailOkapiClient.sendEmail(EMAIL_ENDPOINT, JsonObject.mapFrom(emailEntity).toString());
  }

  private EmailEntity getEmailEntity(LedgerFiscalYearRollover rollover, String linkToRolloverLedger, String ledgerName, JsonObject user) {
    String email = user.getJsonObject(PERSONAL_KEY).getString(EMAIL_KEY);
    EmailEntity emailEntity = new EmailEntity();
    emailEntity.setNotificationId(UUID.randomUUID().toString());
    emailEntity.setBody(getRolloverBody(rollover, linkToRolloverLedger, ledgerName, user));
    emailEntity.setHeader(EMAIL_HEADER);
    emailEntity.setTo(email);
    emailEntity.setOutputFormat(MediaType.TEXT_HTML);
    return emailEntity;
  }

  private String getRolloverBody(LedgerFiscalYearRollover rollover, String linkToRolloverLedger, String ledgerName, JsonObject user) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FORMAT_PATTERN).withZone(ZoneId.of(UTC_ZONE_ID));
    return String.format(LedgerFiscalYearRollover.RolloverType.COMMIT.equals(rollover.getRolloverType()) ? COMMIT_MESSAGE : PREVIEW_MESSAGE,
      user.getString(USERNAME_KEY),
      formatter.format(rollover.getMetadata().getCreatedDate().toInstant()),
      ledgerName,
      linkToRolloverLedger);
  }

  private Map<String, String> getHeaders(RequestContext requestContext) {
    Map<String, String> newHeaders = new HashMap<>();
    newHeaders.put(ACCEPT_KEY, MediaType.TEXT_PLAIN);
    newHeaders.put(OKAPI_TENANT, requestContext.getHeaders().get(OKAPI_TENANT.toLowerCase(Locale.ROOT)));
    newHeaders.put(OKAPI_TOKEN, requestContext.getHeaders().get(OKAPI_TOKEN));
    newHeaders.put(OKAPI_URL, requestContext.getHeaders().get(OKAPI_URL));
    return newHeaders;
  }

  private String createRolloverLedgerLink(String hostAddress, String ledgerId) {
    return hostAddress + String.format(ROLLOVER_LOGS_ENDPOINT, ledgerId);
  }

  private Future<JsonObject> getHostAddress(RequestContext requestContext) {
    return configurationRestClient.get(CONFIGURATION_QUERY, 0, 1, requestContext, JsonObject.class);
  }

  private Future<JsonObject> getCurrentUser(RequestContext requestContext) {
    return userRestClient.getById(requestContext.getHeaders().get(OKAPI_USER_ID), requestContext, JsonObject.class);
  }
}
