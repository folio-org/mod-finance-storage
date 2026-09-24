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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.folio.CopilotGenerated;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetAuditEvent;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundAuditEvent;
import org.folio.rest.jaxrs.model.FyFinanceData;
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.resource.FinanceStorageFinanceData;
import org.folio.rest.persist.HelperUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

@CopilotGenerated(model = "Claude Opus 5.5")
public class FinanceDataAuditEventTest extends TestBase {

  private static final String FINANCE_DATA_AUDIT_EVENT_TENANT = "financedataauditeventtenant";
  private static final Header FINANCE_DATA_AUDIT_EVENT_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, FINANCE_DATA_AUDIT_EVENT_TENANT);
  private static final Header USER_ID_HEADER = new Header(OKAPI_USERID_HEADER, "28d1057c-d137-11e8-a8d5-f2801f1b9fd1");
  private static final String FINANCE_DATA_ENDPOINT = HelperUtils.getEndpoint(FinanceStorageFinanceData.class);
  private static final String ACQ_FUND_CHANGED_TOPIC = "ACQ_FUND_CHANGED";
  private static final String ACQ_BUDGET_CHANGED_TOPIC = "ACQ_BUDGET_CHANGED";
  private static final String FISCAL_YEAR_CODE = "FY2026";

  private static TenantJob tenantJob;

  @AfterAll
  static void deleteTable() {
    if (tenantJob != null) {
      deleteTenant(tenantJob, FINANCE_DATA_AUDIT_EVENT_TENANT_HEADER);
    }
  }

  @Test
  void shouldSendSingleEventPerChangedFundAndBudgetOnFinanceDataUpdate() {
    tenantJob = prepareTenant(FINANCE_DATA_AUDIT_EVENT_TENANT_HEADER, false, true);

    var fiscalYearId = UUID.randomUUID().toString();
    var ledgerId = UUID.randomUUID().toString();
    var fundWithBudget = fund(ledgerId, "AUDITFUND1");
    var fundWithoutBudget = fund(ledgerId, "AUDITFUND2");
    var existingBudget = new Budget()
      .withId(UUID.randomUUID().toString())
      .withName("AUDITFUND1-" + FISCAL_YEAR_CODE)
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundWithBudget.getId())
      .withInitialAllocation(100.0)
      .withAllowableExpenditure(101.0)
      .withAllowableEncumbrance(102.0);

    var fiscalYear = new JsonObject(getFile(FISCAL_YEAR.getPathToSampleFile())).mapTo(FiscalYear.class)
      .withId(fiscalYearId)
      .withCode(FISCAL_YEAR_CODE)
      .withPeriodStart(Date.from(Instant.now().minus(100, ChronoUnit.DAYS)))
      .withPeriodEnd(Date.from(Instant.now().plus(100, ChronoUnit.DAYS)));
    createEntity(FISCAL_YEAR.getEndpoint(), fiscalYear, FINANCE_DATA_AUDIT_EVENT_TENANT_HEADER);
    createEntity(LEDGER.getEndpoint(), new Ledger()
      .withId(ledgerId)
      .withCode("AUDITLEDGER")
      .withName("Audit ledger")
      .withFiscalYearOneId(fiscalYearId)
      .withLedgerStatus(Ledger.LedgerStatus.ACTIVE), FINANCE_DATA_AUDIT_EVENT_TENANT_HEADER);
    createEntity(FUND.getEndpoint(), fundWithBudget, FINANCE_DATA_AUDIT_EVENT_TENANT_HEADER);
    createEntity(FUND.getEndpoint(), fundWithoutBudget, FINANCE_DATA_AUDIT_EVENT_TENANT_HEADER);
    createEntity(BUDGET.getEndpoint(), existingBudget, FINANCE_DATA_AUDIT_EVENT_TENANT_HEADER);

    // the existing budget is changed twice in one request: by the update and by the allocation
    var existingBudgetData = new FyFinanceData()
      .withFiscalYearId(fiscalYearId)
      .withFiscalYearCode(FISCAL_YEAR_CODE)
      .withFundId(fundWithBudget.getId())
      .withFundStatus(Fund.FundStatus.ACTIVE.value())
      .withFundDescription("Updated description")
      .withBudgetId(existingBudget.getId())
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE.value())
      .withBudgetAllowableExpenditure(150.0)
      .withBudgetAllowableEncumbrance(102.0)
      .withBudgetAllocationChange(50.0);
    // the new budget is created, updated and allocated in one request; its fund has no changes
    var newBudgetData = new FyFinanceData()
      .withFiscalYearId(fiscalYearId)
      .withFiscalYearCode(FISCAL_YEAR_CODE)
      .withFundId(fundWithoutBudget.getId())
      .withFundStatus(Fund.FundStatus.ACTIVE.value())
      .withBudgetName("AUDITFUND2-" + FISCAL_YEAR_CODE)
      .withBudgetAllocationChange(25.0);
    var collection = new FyFinanceDataCollection()
      .withFyFinanceData(List.of(existingBudgetData, newBudgetData))
      .withUpdateType(FyFinanceDataCollection.UpdateType.COMMIT)
      .withTotalRecords(2);

    var updatedCollection = given()
      .header(FINANCE_DATA_AUDIT_EVENT_TENANT_HEADER)
      .header(USER_ID_HEADER)
      .contentType(ContentType.JSON)
      .body(valueAsString(collection))
      .put(storageUrl(FINANCE_DATA_ENDPOINT))
      .then().log().ifValidationFails()
      .statusCode(200)
      .extract().as(FyFinanceDataCollection.class);
    var newBudgetId = updatedCollection.getFyFinanceData().stream()
      .filter(data -> fundWithoutBudget.getId().equals(data.getFundId()))
      .findFirst().orElseThrow()
      .getBudgetId();

    // 2 Create events for the test data + 1 Edit event for the fund with the new description
    var fundEvents = awaitEvents(ACQ_FUND_CHANGED_TOPIC, 3, FundAuditEvent.class);
    var fundEditEvents = filter(fundEvents, e -> e.getAction() == FundAuditEvent.Action.EDIT, FundAuditEvent::getFundId);
    assertEquals(List.of(fundWithBudget.getId()), fundEditEvents);
    var fundEditEvent = fundEvents.stream().filter(e -> e.getAction() == FundAuditEvent.Action.EDIT).findFirst().orElseThrow();
    assertEquals(USER_ID_HEADER.getValue(), fundEditEvent.getUserId());
    assertEquals("Updated description", fundEditEvent.getFundSnapshot().getDescription());
    assertEquals("Description", fundEditEvent.getOriginalFundSnapshot().getDescription());

    // 1 Create event for the test data + 1 Create event for the new budget + 1 Edit event for the existing budget
    var budgetEvents = awaitEvents(ACQ_BUDGET_CHANGED_TOPIC, 3, BudgetAuditEvent.class);
    assertEquals(3, budgetEvents.size());
    assertEquals(List.of(existingBudget.getId()),
      filter(budgetEvents, e -> e.getAction() == BudgetAuditEvent.Action.EDIT, BudgetAuditEvent::getBudgetId));
    assertEquals(Stream.of(existingBudget.getId(), newBudgetId).sorted().toList(),
      filter(budgetEvents, e -> e.getAction() == BudgetAuditEvent.Action.CREATE, BudgetAuditEvent::getBudgetId));

    var budgetEditEvent = budgetEvents.stream()
      .filter(e -> e.getAction() == BudgetAuditEvent.Action.EDIT)
      .findFirst().orElseThrow();
    assertEquals(USER_ID_HEADER.getValue(), budgetEditEvent.getUserId());
    // both changes of the request are in the single event
    assertEquals(101.0, budgetEditEvent.getOriginalBudgetSnapshot().getAllowableExpenditure());
    assertEquals(150.0, budgetEditEvent.getBudgetSnapshot().getAllowableExpenditure());
    assertEquals(100.0, budgetEditEvent.getOriginalBudgetSnapshot().getAllocated());
    assertEquals(150.0, budgetEditEvent.getBudgetSnapshot().getAllocated());

    var newBudgetCreateEvent = budgetEvents.stream()
      .filter(e -> newBudgetId.equals(e.getBudgetId()))
      .findFirst().orElseThrow();
    assertNull(newBudgetCreateEvent.getOriginalBudgetSnapshot());
    // the create event already contains the allocation of the same request
    assertEquals(25.0, newBudgetCreateEvent.getBudgetSnapshot().getAllocated());

    purge(FINANCE_DATA_AUDIT_EVENT_TENANT_HEADER);
  }

  private Fund fund(String ledgerId, String code) {
    return new Fund()
      .withId(UUID.randomUUID().toString())
      .withCode(code)
      .withName(code)
      .withDescription("Description")
      .withLedgerId(ledgerId)
      .withFundStatus(Fund.FundStatus.ACTIVE);
  }

  private <T> List<T> awaitEvents(String topic, int expectedCount, Class<T> eventClass) {
    List<T> events = new ArrayList<>();
    await().atMost(60, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> {
      events.clear();
      checkKafkaEventSent(FINANCE_DATA_AUDIT_EVENT_TENANT, topic)
        .forEach(value -> events.add(Json.decodeValue(value, eventClass)));
      return events.size() >= expectedCount;
    });
    return events;
  }

  private <T> List<String> filter(List<T> events, Predicate<T> predicate, Function<T, String> idGetter) {
    return events.stream().filter(predicate).map(idGetter).sorted().toList();
  }

}
