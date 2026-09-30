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
import org.junit.jupiter.api.Test;

@CopilotGenerated(model = "Claude Opus 5")
public class AuditEventProducerTest {

  private static final String CREATOR_ID = "28d1057c-d137-11e8-a8d5-f2801f1b9fd1";
  private static final String EDITOR_ID = "4b6c5a2e-8b2d-4f6e-9a3c-1d2e3f4a5b6c";
  private static final Date CREATED_DATE = new Date(1_000_000L);
  private static final Date EDITED_DATE = new Date(2_000_000L);

  private final AuditEventProducer producer = new AuditEventProducer(null);

  @Test
  void shouldBuildFundCreateEvent() {
    var fund = fund(metadata(CREATED_DATE, CREATOR_ID));

    var event = producer.getAuditEvent(fund, null, FundAuditEvent.Action.CREATE);

    assertNotNull(event.getId());
    assertNotNull(event.getEventDate());
    assertEquals(FundAuditEvent.Action.CREATE, event.getAction());
    assertEquals(fund.getId(), event.getFundId());
    assertEquals(CREATOR_ID, event.getUserId());
    assertEquals(CREATED_DATE, event.getActionDate());
    assertEquals(fund, event.getFundSnapshot());
    assertNull(event.getOriginalFundSnapshot());
  }

  @Test
  void shouldBuildFundEditEventWithCreationMetadataOfOriginal() {
    var original = fund(metadata(CREATED_DATE, CREATOR_ID));
    // RMB stamps all metadata fields of the PUT body with the edit, including the created ones
    var edited = fund(metadata(EDITED_DATE, EDITOR_ID)).withId(original.getId());

    var event = producer.getAuditEvent(edited, original, FundAuditEvent.Action.EDIT);

    assertEquals(FundAuditEvent.Action.EDIT, event.getAction());
    assertEquals(EDITOR_ID, event.getUserId());
    assertEquals(EDITED_DATE, event.getActionDate());
    assertEquals(original, event.getOriginalFundSnapshot());
    assertCreatedByOriginalAndUpdatedByEdit(event.getFundSnapshot().getMetadata());
  }

  @Test
  void shouldBuildFundEventWithoutMetadata() {
    var fund = fund(null);

    var event = producer.getAuditEvent(fund, null, FundAuditEvent.Action.CREATE);

    assertEquals(fund.getId(), event.getFundId());
    assertNull(event.getActionDate());
    assertNull(event.getUserId());
  }

  @Test
  void shouldBuildBudgetCreateEvent() {
    var budget = budget(metadata(CREATED_DATE, CREATOR_ID));

    var event = producer.getAuditEvent(budget, null, BudgetAuditEvent.Action.CREATE);

    assertNotNull(event.getId());
    assertEquals(BudgetAuditEvent.Action.CREATE, event.getAction());
    assertEquals(budget.getId(), event.getBudgetId());
    assertEquals(CREATOR_ID, event.getUserId());
    assertEquals(budget, event.getBudgetSnapshot());
    assertNull(event.getOriginalBudgetSnapshot());
  }

  @Test
  void shouldBuildBudgetEditEventWithCreationMetadataOfOriginal() {
    var original = budget(metadata(CREATED_DATE, CREATOR_ID));
    var edited = budget(metadata(EDITED_DATE, EDITOR_ID)).withId(original.getId());

    var event = producer.getAuditEvent(edited, original, BudgetAuditEvent.Action.EDIT);

    assertEquals(BudgetAuditEvent.Action.EDIT, event.getAction());
    assertEquals(EDITOR_ID, event.getUserId());
    assertEquals(original, event.getOriginalBudgetSnapshot());
    assertCreatedByOriginalAndUpdatedByEdit(event.getBudgetSnapshot().getMetadata());
  }

  private void assertCreatedByOriginalAndUpdatedByEdit(Metadata metadata) {
    assertEquals(CREATED_DATE, metadata.getCreatedDate());
    assertEquals(CREATOR_ID, metadata.getCreatedByUserId());
    assertEquals(EDITED_DATE, metadata.getUpdatedDate());
    assertEquals(EDITOR_ID, metadata.getUpdatedByUserId());
  }

  private Metadata metadata(Date date, String userId) {
    return new Metadata()
      .withCreatedDate(date)
      .withCreatedByUserId(userId)
      .withUpdatedDate(date)
      .withUpdatedByUserId(userId);
  }

  private Fund fund(Metadata metadata) {
    return new Fund()
      .withId(UUID.randomUUID().toString())
      .withCode("HIST")
      .withName("History")
      .withFundStatus(Fund.FundStatus.ACTIVE)
      .withMetadata(metadata);
  }

  private Budget budget(Metadata metadata) {
    return new Budget()
      .withId(UUID.randomUUID().toString())
      .withName("History FY2026")
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withFundId(UUID.randomUUID().toString())
      .withFiscalYearId(UUID.randomUUID().toString())
      .withMetadata(metadata);
  }

}
