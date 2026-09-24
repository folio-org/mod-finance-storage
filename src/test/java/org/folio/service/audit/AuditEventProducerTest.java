package org.folio.service.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import java.util.UUID;

import org.folio.CopilotGenerated;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetAuditEvent;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundAuditEvent;
import org.folio.rest.jaxrs.model.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CopilotGenerated(model = "Claude Opus 5")
public class AuditEventProducerTest {

  private static final String USER_ID = "28d1057c-d137-11e8-a8d5-f2801f1b9fd1";

  private AuditEventProducer producer;

  @BeforeEach
  void setUp() {
    producer = new AuditEventProducer(null);
  }

  @Test
  void shouldBuildCreateEventWithoutOriginalSnapshot() {
    var fund = fund(new Date(), new Date());

    var event = producer.getAuditEvent(fund, null, FundAuditEvent.Action.CREATE);

    assertNotNull(event.getId());
    assertNotNull(event.getEventDate());
    assertEquals(FundAuditEvent.Action.CREATE, event.getAction());
    assertEquals(fund.getId(), event.getFundId());
    assertEquals(USER_ID, event.getUserId());
    assertEquals(fund.getMetadata().getUpdatedDate(), event.getActionDate());
    assertEquals(fund, event.getFundSnapshot());
    assertNull(event.getOriginalFundSnapshot());
  }

  @Test
  void shouldRestoreCreationMetadataOnEditEvent() {
    var createdDate = new Date(1_000_000L);
    var original = fund(createdDate, createdDate);
    // RMB stamps the PUT body metadata from the request headers, so the created fields describe the edit
    var editedFund = fund(new Date(), new Date());
    editedFund.setId(original.getId());

    var event = producer.getAuditEvent(editedFund, original, FundAuditEvent.Action.EDIT);

    assertEquals(FundAuditEvent.Action.EDIT, event.getAction());
    assertEquals(original, event.getOriginalFundSnapshot());
    assertEquals(createdDate, event.getFundSnapshot().getMetadata().getCreatedDate());
    assertEquals(USER_ID, event.getFundSnapshot().getMetadata().getCreatedByUserId());
  }

  @Test
  void shouldBuildEventWhenMetadataIsMissing() {
    var fund = new Fund().withId(UUID.randomUUID().toString());

    var event = producer.getAuditEvent(fund, null, FundAuditEvent.Action.CREATE);

    assertEquals(fund.getId(), event.getFundId());
    assertNull(event.getActionDate());
    assertNull(event.getUserId());
  }

  @Test
  void shouldBuildBudgetCreateEventWithoutOriginalSnapshot() {
    var budget = budget(new Date(), new Date());

    var event = producer.getAuditEvent(budget, null, BudgetAuditEvent.Action.CREATE);

    assertNotNull(event.getId());
    assertEquals(BudgetAuditEvent.Action.CREATE, event.getAction());
    assertEquals(budget.getId(), event.getBudgetId());
    assertEquals(USER_ID, event.getUserId());
    assertEquals(budget, event.getBudgetSnapshot());
    assertNull(event.getOriginalBudgetSnapshot());
  }

  @Test
  void shouldRestoreCreationMetadataOnBudgetEditEvent() {
    var createdDate = new Date(1_000_000L);
    var original = budget(createdDate, createdDate);
    var editedBudget = budget(new Date(), new Date());
    editedBudget.setId(original.getId());

    var event = producer.getAuditEvent(editedBudget, original, BudgetAuditEvent.Action.EDIT);

    assertEquals(BudgetAuditEvent.Action.EDIT, event.getAction());
    assertEquals(original, event.getOriginalBudgetSnapshot());
    assertEquals(createdDate, event.getBudgetSnapshot().getMetadata().getCreatedDate());
    assertEquals(USER_ID, event.getBudgetSnapshot().getMetadata().getCreatedByUserId());
  }

  private Budget budget(Date createdDate, Date updatedDate) {
    return new Budget()
      .withId(UUID.randomUUID().toString())
      .withName("History FY2026")
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withFundId(UUID.randomUUID().toString())
      .withFiscalYearId(UUID.randomUUID().toString())
      .withMetadata(new Metadata()
        .withCreatedDate(createdDate)
        .withCreatedByUserId(USER_ID)
        .withUpdatedDate(updatedDate)
        .withUpdatedByUserId(USER_ID));
  }

  private Fund fund(Date createdDate, Date updatedDate) {
    return new Fund()
      .withId(UUID.randomUUID().toString())
      .withCode("HIST")
      .withName("History")
      .withFundStatus(Fund.FundStatus.ACTIVE)
      .withMetadata(new Metadata()
        .withCreatedDate(createdDate)
        .withCreatedByUserId(USER_ID)
        .withUpdatedDate(updatedDate)
        .withUpdatedByUserId(USER_ID));
  }

}
