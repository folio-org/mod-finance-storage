package org.folio.service.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.CopilotGenerated;
import org.folio.dao.audit.AuditOutboxEventLogDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetAuditEvent;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundAuditEvent;
import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.persist.DBConn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.Future;
import io.vertx.core.json.Json;

@ExtendWith(MockitoExtension.class)
@CopilotGenerated(model = "Claude Opus 5")
public class AuditOutboxServiceTest {

  @Mock
  private AuditOutboxEventLogDAO outboxEventLogDAO;
  @Mock
  private AuditEventProducer producer;
  @Mock
  private DBConn conn;
  @InjectMocks
  private AuditOutboxService auditOutboxService;

  @Captor
  private ArgumentCaptor<OutboxEventLog> eventLogCaptor;

  @Test
  void shouldSaveCreateOutboxLogWithoutOriginalEntity() {
    var fund = fund();
    when(outboxEventLogDAO.saveEventLog(any(), any())).thenReturn(Future.succeededFuture());

    auditOutboxService.saveFundOutboxLog(conn, fund, FundAuditEvent.Action.CREATE);

    verify(outboxEventLogDAO).saveEventLog(any(), eventLogCaptor.capture());
    var eventLog = eventLogCaptor.getValue();
    assertNotNull(eventLog.getEventId());
    assertEquals(OutboxEventLog.EntityType.FUND, eventLog.getEntityType());
    assertEquals(FundAuditEvent.Action.CREATE.value(), eventLog.getAction());

    var wrapper = auditOutboxService.decodePayload(eventLog.getPayload(), Fund.class);
    assertEquals(fund.getId(), wrapper.getEntity().getId());
    assertNull(wrapper.getOriginalEntity());
  }

  @Test
  void shouldSaveEditOutboxLogWithOriginalEntity() {
    var original = fund();
    var fund = Json.decodeValue(Json.encode(original), Fund.class).withFundStatus(Fund.FundStatus.FROZEN);
    when(outboxEventLogDAO.saveEventLog(any(), any())).thenReturn(Future.succeededFuture());

    auditOutboxService.saveFundOutboxLog(conn, fund, original, FundAuditEvent.Action.EDIT);

    verify(outboxEventLogDAO).saveEventLog(any(), eventLogCaptor.capture());
    var eventLog = eventLogCaptor.getValue();
    assertEquals(FundAuditEvent.Action.EDIT.value(), eventLog.getAction());

    var wrapper = auditOutboxService.decodePayload(eventLog.getPayload(), Fund.class);
    assertEquals(Fund.FundStatus.FROZEN, wrapper.getEntity().getFundStatus());
    assertEquals(Fund.FundStatus.ACTIVE, wrapper.getOriginalEntity().getFundStatus());
  }

  @Test
  void shouldSaveEditOutboxLogForBudget() {
    var original = budget();
    var budget = Json.decodeValue(Json.encode(original), Budget.class).withBudgetStatus(Budget.BudgetStatus.FROZEN);
    when(outboxEventLogDAO.saveEventLog(any(), any())).thenReturn(Future.succeededFuture());

    auditOutboxService.saveBudgetOutboxLog(conn, budget, original, BudgetAuditEvent.Action.EDIT);

    verify(outboxEventLogDAO).saveEventLog(any(), eventLogCaptor.capture());
    var eventLog = eventLogCaptor.getValue();
    assertEquals(OutboxEventLog.EntityType.BUDGET, eventLog.getEntityType());
    assertEquals(BudgetAuditEvent.Action.EDIT.value(), eventLog.getAction());

    var wrapper = auditOutboxService.decodePayload(eventLog.getPayload(), Budget.class);
    assertEquals(Budget.BudgetStatus.FROZEN, wrapper.getEntity().getBudgetStatus());
    assertEquals(Budget.BudgetStatus.ACTIVE, wrapper.getOriginalEntity().getBudgetStatus());
  }

  @Test
  void shouldSkipBrokenEventLogsAndSendValidOnes() {
    var fund = fund();
    var validLog = eventLog(OutboxEventLog.EntityType.FUND, FundAuditEvent.Action.CREATE.value(),
      Json.encode(AuditEntityWrapper.of(fund, null)));
    var unknownActionLog = eventLog(OutboxEventLog.EntityType.FUND, "Unknown", Json.encode(AuditEntityWrapper.of(fund, null)));
    var malformedPayloadLog = eventLog(OutboxEventLog.EntityType.BUDGET, BudgetAuditEvent.Action.EDIT.value(), "not a json");
    var missingEntityLog = eventLog(OutboxEventLog.EntityType.BUDGET, BudgetAuditEvent.Action.EDIT.value(), "{}");
    var unknownEntityTypeLog = eventLog(null, FundAuditEvent.Action.CREATE.value(), Json.encode(AuditEntityWrapper.of(fund, null)));
    when(producer.sendFundEvent(any(), any(), any(), any())).thenReturn(Future.succeededFuture());

    var futures = auditOutboxService.sendEventLogsToKafka(
      List.of(unknownActionLog, malformedPayloadLog, missingEntityLog, unknownEntityTypeLog, validLog), Map.of());

    assertEquals(5, futures.size());
    assertTrue(futures.stream().allMatch(Future::succeeded));
    verify(producer).sendFundEvent(any(), any(), eq(FundAuditEvent.Action.CREATE), any());
    verifyNoMoreInteractions(producer);
  }

  @Test
  void shouldSaveBatchOutboxLogsWithMatchingOriginalBudgets() {
    var otherOriginal = budget();
    var changedOriginal = budget();
    var changed = copy(changedOriginal, Budget.class).withBudgetStatus(Budget.BudgetStatus.FROZEN);
    var created = budget();
    when(outboxEventLogDAO.saveEventLog(any(), any())).thenReturn(Future.succeededFuture());

    var result = auditOutboxService.saveBudgetOutboxLogs(conn, List.of(changed, created),
      List.of(otherOriginal, changedOriginal), BudgetAuditEvent.Action.EDIT);

    assertTrue(result.succeeded());
    verify(outboxEventLogDAO, times(2)).saveEventLog(any(), eventLogCaptor.capture());
    var wrappers = eventLogCaptor.getAllValues().stream()
      .map(eventLog -> auditOutboxService.decodePayload(eventLog.getPayload(), Budget.class))
      .toList();
    var changedWrapper = wrappers.stream()
      .filter(wrapper -> changed.getId().equals(wrapper.getEntity().getId()))
      .findFirst().orElseThrow();
    assertEquals(Budget.BudgetStatus.FROZEN, changedWrapper.getEntity().getBudgetStatus());
    assertEquals(Budget.BudgetStatus.ACTIVE, changedWrapper.getOriginalEntity().getBudgetStatus());
    var createdWrapper = wrappers.stream()
      .filter(wrapper -> created.getId().equals(wrapper.getEntity().getId()))
      .findFirst().orElseThrow();
    assertNull(createdWrapper.getOriginalEntity());
  }

  private OutboxEventLog eventLog(OutboxEventLog.EntityType entityType, String action, String payload) {
    return new OutboxEventLog()
      .withEventId(UUID.randomUUID().toString())
      .withEntityType(entityType)
      .withAction(action)
      .withPayload(payload);
  }

  private <T> T copy(T entity, Class<T> clazz) {
    return Json.decodeValue(Json.encode(entity), clazz);
  }

  private Budget budget() {
    return new Budget()
      .withId(UUID.randomUUID().toString())
      .withName("History FY2026")
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withFundId(UUID.randomUUID().toString())
      .withFiscalYearId(UUID.randomUUID().toString());
  }

  private Fund fund() {
    return new Fund()
      .withId(UUID.randomUUID().toString())
      .withCode("HIST")
      .withName("History")
      .withFundStatus(Fund.FundStatus.ACTIVE);
  }

}
