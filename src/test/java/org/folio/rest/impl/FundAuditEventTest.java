package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.folio.StorageTestSuite.checkKafkaEventSent;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundAuditEvent;
import org.folio.rest.jaxrs.model.FundCollection;
import org.folio.rest.jaxrs.model.TenantJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.Json;

public class FundAuditEventTest extends TestBase {

  private static final String FUND_ENDPOINT = FUND.getEndpoint();
  private static final String FUND_AUDIT_EVENT_TENANT = "fundauditeventtenant";
  private static final String ACQ_FUND_CHANGED_TOPIC = "ACQ_FUND_CHANGED";
  private static final Header FUND_AUDIT_EVENT_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, FUND_AUDIT_EVENT_TENANT);
  private static final Header USER_ID_HEADER = new Header(OKAPI_USERID_HEADER, "28d1057c-d137-11e8-a8d5-f2801f1b9fd1");

  private static TenantJob tenantJob;

  @AfterAll
  static void deleteTable() {
    deleteTenant(tenantJob, FUND_AUDIT_EVENT_TENANT_HEADER);
  }

  @Test
  void shouldSendCreateAndEditEventsOnFundChanges() {
    tenantJob = prepareTenant(FUND_AUDIT_EVENT_TENANT_HEADER, true, true);

    var existingFund = getData(FUND_ENDPOINT, FUND_AUDIT_EVENT_TENANT_HEADER)
      .as(FundCollection.class)
      .getFunds()
      .getFirst();
    var fund = new Fund()
      .withId(UUID.randomUUID().toString())
      .withCode("AUDITEVENTTEST")
      .withName("Audit event test fund")
      .withFundStatus(Fund.FundStatus.ACTIVE)
      .withLedgerId(existingFund.getLedgerId())
      .withExternalAccountNo("1234567890");

    var createdFund = given()
      .header(FUND_AUDIT_EVENT_TENANT_HEADER)
      .header(USER_ID_HEADER)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(valueAsString(fund))
      .post(storageUrl(FUND_ENDPOINT))
      .then().log().ifValidationFails()
      .statusCode(201)
      .extract().as(Fund.class);

    createdFund.setFundStatus(Fund.FundStatus.FROZEN);
    createdFund.setDescription("Updated by the audit event test");

    given()
      .pathParam("id", fund.getId())
      .header(FUND_AUDIT_EVENT_TENANT_HEADER)
      .header(USER_ID_HEADER)
      .contentType(ContentType.JSON)
      .body(valueAsString(createdFund))
      .put(storageUrl(FUND_ENDPOINT + "/{id}"))
      .then().log().ifValidationFails()
      .statusCode(204);

    var events = awaitFundEvents(2);

    var createEvent = findEvent(events, fund.getId(), FundAuditEvent.Action.CREATE);
    assertEquals(fund.getId(), createEvent.getFundId());
    assertEquals(USER_ID_HEADER.getValue(), createEvent.getUserId());
    assertNotNull(createEvent.getActionDate());
    assertNull(createEvent.getOriginalFundSnapshot());
    assertEquals(Fund.FundStatus.ACTIVE, createEvent.getFundSnapshot().getFundStatus());

    var editEvent = findEvent(events, fund.getId(), FundAuditEvent.Action.EDIT);
    assertEquals(Fund.FundStatus.FROZEN, editEvent.getFundSnapshot().getFundStatus());
    assertNotNull(editEvent.getOriginalFundSnapshot());
    assertEquals(Fund.FundStatus.ACTIVE, editEvent.getOriginalFundSnapshot().getFundStatus());
    // the create date is taken from the stored fund, not from the headers of the edit request
    assertEquals(editEvent.getOriginalFundSnapshot().getMetadata().getCreatedDate(),
      editEvent.getFundSnapshot().getMetadata().getCreatedDate());

    purge(FUND_AUDIT_EVENT_TENANT_HEADER);
  }

  private List<FundAuditEvent> awaitFundEvents(int expectedCount) {
    List<FundAuditEvent> events = new ArrayList<>();
    await().atMost(60, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
      events.clear();
      checkKafkaEventSent(FUND_AUDIT_EVENT_TENANT, ACQ_FUND_CHANGED_TOPIC)
        .forEach(value -> events.add(Json.decodeValue(value, FundAuditEvent.class)));
      return events.size() >= expectedCount;
    });
    return events;
  }

  private FundAuditEvent findEvent(List<FundAuditEvent> events, String fundId, FundAuditEvent.Action action) {
    return events.stream()
      .filter(event -> fundId.equals(event.getFundId()) && action == event.getAction())
      .findFirst()
      .orElseThrow(() -> new AssertionError("No %s event found for fund %s".formatted(action, fundId)));
  }

}
