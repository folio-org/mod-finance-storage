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
import java.util.function.Function;
import java.util.stream.Collectors;

import org.folio.CopilotGenerated;
import org.folio.dao.audit.AuditOutboxEventLogDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetAuditEvent;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundAuditEvent;
import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.jaxrs.model.OutboxEventLog.EntityType;
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
  void shouldSaveFundCreateOutboxLogWithoutOriginal() {
    var fund = fund();
    givenEventLogsAreSaved();

    auditOutboxService.saveFundOutboxLog(conn, fund, FundAuditEvent.Action.CREATE);

    var eventLog = savedEventLog();
    assertNotNull(eventLog.getEventId());
    assertEquals(EntityType.FUND, eventLog.getEntityType());
    assertEquals(FundAuditEvent.Action.CREATE.value(), eventLog.getAction());
    var payload = decode(eventLog, Fund.class);
    assertEquals(fund.getId(), payload.getEntity().getId());
    assertNull(payload.getOriginalEntity());
  }

  @Test
  void shouldSaveFundEditOutboxLogWithOriginal() {
    var original = fund();
    var fund = copy(original).withFundStatus(Fund.FundStatus.FROZEN);
    givenEventLogsAreSaved();

    auditOutboxService.saveFundOutboxLog(conn, fund, original, FundAuditEvent.Action.EDIT);

    var eventLog = savedEventLog();
    assertEquals(FundAuditEvent.Action.EDIT.value(), eventLog.getAction());
    var payload = decode(eventLog, Fund.class);
    assertEquals(Fund.FundStatus.FROZEN, payload.getEntity().getFundStatus());
    assertEquals(Fund.FundStatus.ACTIVE, payload.getOriginalEntity().getFundStatus());
  }

  @Test
  void shouldSaveBudgetEditOutboxLogWithOriginal() {
    var original = budget();
    var budget = copy(original).withBudgetStatus(Budget.BudgetStatus.FROZEN);
    givenEventLogsAreSaved();

    auditOutboxService.saveBudgetOutboxLog(conn, budget, original, BudgetAuditEvent.Action.EDIT);

    var eventLog = savedEventLog();
    assertEquals(EntityType.BUDGET, eventLog.getEntityType());
    assertEquals(BudgetAuditEvent.Action.EDIT.value(), eventLog.getAction());
    var payload = decode(eventLog, Budget.class);
    assertEquals(Budget.BudgetStatus.FROZEN, payload.getEntity().getBudgetStatus());
    assertEquals(Budget.BudgetStatus.ACTIVE, payload.getOriginalEntity().getBudgetStatus());
  }

  @Test
  void shouldSaveBudgetOutboxLogsInBatchWithMatchingOriginals() {
    var changedOriginal = budget();
    var changed = copy(changedOriginal).withBudgetStatus(Budget.BudgetStatus.FROZEN);
    var withoutOriginal = budget();
    var unrelatedOriginal = budget();
    givenEventLogsAreSaved();

    var result = auditOutboxService.saveBudgetOutboxLogs(conn, List.of(changed, withoutOriginal),
      List.of(unrelatedOriginal, changedOriginal), BudgetAuditEvent.Action.EDIT);

    assertTrue(result.succeeded());
    var payloads = savedBudgetPayloadsById(2);
    assertEquals(Budget.BudgetStatus.FROZEN, payloads.get(changed.getId()).getEntity().getBudgetStatus());
    assertEquals(Budget.BudgetStatus.ACTIVE, payloads.get(changed.getId()).getOriginalEntity().getBudgetStatus());
    assertNull(payloads.get(withoutOriginal.getId()).getOriginalEntity());
  }

  @Test
  void shouldSkipBrokenEventLogsAndSendValidOnes() {
    var validPayload = Json.encode(AuditEntityWrapper.of(fund(), null));
    var eventLogs = List.of(
      eventLog(EntityType.FUND, "Unknown action", validPayload),
      eventLog(EntityType.BUDGET, BudgetAuditEvent.Action.EDIT.value(), "not a json"),
      eventLog(EntityType.BUDGET, BudgetAuditEvent.Action.EDIT.value(), "{}"), // no entity in the payload
      eventLog(null, FundAuditEvent.Action.CREATE.value(), validPayload),
      eventLog(EntityType.FUND, FundAuditEvent.Action.CREATE.value(), validPayload)); // the only valid one
    when(producer.sendFundEvent(any(), any(), any(), any())).thenReturn(Future.succeededFuture());

    var results = auditOutboxService.sendEventLogsToKafka(eventLogs, Map.of());

    // broken logs complete successfully, so they are deleted together with the sent ones
    assertTrue(results.stream().allMatch(Future::succeeded));
    verify(producer).sendFundEvent(any(), any(), eq(FundAuditEvent.Action.CREATE), any());
    verifyNoMoreInteractions(producer);
  }

  private void givenEventLogsAreSaved() {
    when(outboxEventLogDAO.saveEventLog(any(), any())).thenReturn(Future.succeededFuture());
  }

  private OutboxEventLog savedEventLog() {
    verify(outboxEventLogDAO).saveEventLog(eq(conn), eventLogCaptor.capture());
    return eventLogCaptor.getValue();
  }

  private Map<String, AuditEntityWrapper<Budget>> savedBudgetPayloadsById(int expectedCount) {
    verify(outboxEventLogDAO, times(expectedCount)).saveEventLog(eq(conn), eventLogCaptor.capture());
    return eventLogCaptor.getAllValues().stream()
      .map(eventLog -> decode(eventLog, Budget.class))
      .collect(Collectors.toMap(payload -> payload.getEntity().getId(), Function.identity()));
  }

  private <T> AuditEntityWrapper<T> decode(OutboxEventLog eventLog, Class<T> entityClass) {
    return auditOutboxService.decodePayload(eventLog.getPayload(), entityClass);
  }

  private OutboxEventLog eventLog(EntityType entityType, String action, String payload) {
    return new OutboxEventLog()
      .withEventId(UUID.randomUUID().toString())
      .withEntityType(entityType)
      .withAction(action)
      .withPayload(payload);
  }

  @SuppressWarnings("unchecked")
  private <T> T copy(T entity) {
    return (T) Json.decodeValue(Json.encode(entity), entity.getClass());
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
