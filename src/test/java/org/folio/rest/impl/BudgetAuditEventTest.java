package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.folio.StorageTestSuite.checkKafkaEventSent;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_USERID_HEADER;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TestEntities.BUDGET;
import static org.folio.rest.utils.TestEntities.FISCAL_YEAR;
import static org.folio.rest.utils.TestEntities.FUND;
import static org.folio.rest.utils.TestEntities.LEDGER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetAuditEvent;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.Json;

public class BudgetAuditEventTest extends TestBase {

  private static final String BUDGET_ENDPOINT = BUDGET.getEndpoint();
  private static final String BUDGET_AUDIT_EVENT_TENANT = "budgetauditeventtenant";
  private static final String ACQ_BUDGET_CHANGED_TOPIC = "ACQ_BUDGET_CHANGED";
  private static final Header BUDGET_AUDIT_EVENT_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, BUDGET_AUDIT_EVENT_TENANT);
  private static final Header USER_ID_HEADER = new Header(OKAPI_USERID_HEADER, "28d1057c-d137-11e8-a8d5-f2801f1b9fd1");

  private static final String BATCH_AUDIT_EVENT_TENANT = "budgetbatchauditeventtenant";
  private static final Header BATCH_AUDIT_EVENT_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, BATCH_AUDIT_EVENT_TENANT);
  private static final String BATCH_TRANSACTION_ENDPOINT = "/finance-storage/transactions/batch-all-or-nothing";
  private static final String ALLOCATION_SAMPLE_PATH = "data/transactions/allocations-8.4.0/allocation_AFRICAHIST-FY26.json";

  private static TenantJob tenantJob;
  private static TenantJob batchTenantJob;

  @AfterAll
  static void deleteTable() {
    if (tenantJob != null) {
      deleteTenant(tenantJob, BUDGET_AUDIT_EVENT_TENANT_HEADER);
    }
    if (batchTenantJob != null) {
      deleteTenant(batchTenantJob, BATCH_AUDIT_EVENT_TENANT_HEADER);
    }
  }

  @Test
  void shouldSendCreateAndEditEventsOnBudgetChanges() {
    tenantJob = prepareTenant(BUDGET_AUDIT_EVENT_TENANT_HEADER, false, true);
    givenTestData(BUDGET_AUDIT_EVENT_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()));

    var budget = Json.decodeValue(getFile(BUDGET.getPathToSampleFile()), Budget.class);

    var createdBudget = given()
      .header(BUDGET_AUDIT_EVENT_TENANT_HEADER)
      .header(USER_ID_HEADER)
      .accept(ContentType.JSON)
      .contentType(ContentType.JSON)
      .body(valueAsString(budget))
      .post(storageUrl(BUDGET_ENDPOINT))
      .then().log().ifValidationFails()
      .statusCode(201)
      .extract().as(Budget.class);

    createdBudget.setBudgetStatus(Budget.BudgetStatus.FROZEN);
    createdBudget.setAllowableEncumbrance(50.0);

    given()
      .pathParam("id", createdBudget.getId())
      .header(BUDGET_AUDIT_EVENT_TENANT_HEADER)
      .header(USER_ID_HEADER)
      .contentType(ContentType.JSON)
      .body(valueAsString(createdBudget))
      .put(storageUrl(BUDGET_ENDPOINT + "/{id}"))
      .then().log().ifValidationFails()
      .statusCode(204);

    var events = awaitBudgetEvents(2);

    var createEvent = findEvent(events, createdBudget.getId(), BudgetAuditEvent.Action.CREATE);
    assertEquals(USER_ID_HEADER.getValue(), createEvent.getUserId());
    assertNotNull(createEvent.getActionDate());
    assertNull(createEvent.getOriginalBudgetSnapshot());
    assertEquals(Budget.BudgetStatus.ACTIVE, createEvent.getBudgetSnapshot().getBudgetStatus());

    var editEvent = findEvent(events, createdBudget.getId(), BudgetAuditEvent.Action.EDIT);
    assertEquals(Budget.BudgetStatus.FROZEN, editEvent.getBudgetSnapshot().getBudgetStatus());
    assertNotNull(editEvent.getOriginalBudgetSnapshot());
    assertEquals(Budget.BudgetStatus.ACTIVE, editEvent.getOriginalBudgetSnapshot().getBudgetStatus());
    // the create date is taken from the stored budget, not from the headers of the edit request
    assertEquals(editEvent.getOriginalBudgetSnapshot().getMetadata().getCreatedDate(),
      editEvent.getBudgetSnapshot().getMetadata().getCreatedDate());

    purge(BUDGET_AUDIT_EVENT_TENANT_HEADER);
  }

  @Test
  void shouldSendEditEventOnBudgetChangeByBatchTransactions() {
    batchTenantJob = prepareTenant(BATCH_AUDIT_EVENT_TENANT_HEADER, false, true);
    givenTestData(BATCH_AUDIT_EVENT_TENANT_HEADER,
      Pair.of(FISCAL_YEAR, FISCAL_YEAR.getPathToSampleFile()),
      Pair.of(LEDGER, LEDGER.getPathToSampleFile()),
      Pair.of(FUND, FUND.getPathToSampleFile()),
      Pair.of(BUDGET, BUDGET.getPathToSampleFile()));

    var allocation = Json.decodeValue(getFile(ALLOCATION_SAMPLE_PATH), Transaction.class);
    var batch = new Batch().withTransactionsToCreate(List.of(allocation));

    given()
      .header(BATCH_AUDIT_EVENT_TENANT_HEADER)
      .header(USER_ID_HEADER)
      .contentType(ContentType.JSON)
      .body(valueAsString(batch))
      .post(storageUrl(BATCH_TRANSACTION_ENDPOINT))
      .then().log().ifValidationFails()
      .statusCode(204);

    var events = awaitBudgetEvents(BATCH_AUDIT_EVENT_TENANT, 2);

    var editEvent = findEvent(events, BUDGET.getId(), BudgetAuditEvent.Action.EDIT);
    assertEquals(USER_ID_HEADER.getValue(), editEvent.getUserId());
    assertNotNull(editEvent.getOriginalBudgetSnapshot());
    assertEquals(allocation.getAmount(),
      editEvent.getBudgetSnapshot().getAllocated() - editEvent.getOriginalBudgetSnapshot().getAllocated(), 0.001);

    purge(BATCH_AUDIT_EVENT_TENANT_HEADER);
  }

  private List<BudgetAuditEvent> awaitBudgetEvents(int expectedCount) {
    return awaitBudgetEvents(BUDGET_AUDIT_EVENT_TENANT, expectedCount);
  }

  private List<BudgetAuditEvent> awaitBudgetEvents(String tenant, int expectedCount) {
    List<BudgetAuditEvent> events = new ArrayList<>();
    await().atMost(60, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
      events.clear();
      checkKafkaEventSent(tenant, ACQ_BUDGET_CHANGED_TOPIC)
        .forEach(value -> events.add(Json.decodeValue(value, BudgetAuditEvent.class)));
      return events.size() >= expectedCount;
    });
    return events;
  }

  private BudgetAuditEvent findEvent(List<BudgetAuditEvent> events, String budgetId, BudgetAuditEvent.Action action) {
    return events.stream()
      .filter(event -> budgetId.equals(event.getBudgetId()) && action == event.getAction())
      .findFirst()
      .orElseThrow(() -> new AssertionError("No %s event found for budget %s".formatted(action, budgetId)));
  }

}
