package org.folio.service.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.audit.AuditOutboxEventLogDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.BudgetAuditEvent;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundAuditEvent;
import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.jaxrs.model.OutboxEventLog.EntityType;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.DatabindCodec;

public class AuditOutboxService {

  private static final Logger logger = LogManager.getLogger();

  private final AuditOutboxEventLogDAO outboxEventLogDAO;
  private final AuditEventProducer producer;

  public AuditOutboxService(AuditOutboxEventLogDAO outboxEventLogDAO, AuditEventProducer producer) {
    this.outboxEventLogDAO = outboxEventLogDAO;
    this.producer = producer;
  }

  public Future<Integer> processOutboxEventLogs(Map<String, String> okapiHeaders, Context vertxContext) {
    return new DBClient(vertxContext, okapiHeaders)
      .withTrans(conn -> outboxEventLogDAO.getEventLogs(conn)
        .compose(logs -> {
          if (CollectionUtils.isEmpty(logs)) {
            logger.debug("processOutboxEventLogs:: No logs found in outbox table");
            return Future.succeededFuture(0);
          }
          logger.info("processOutboxEventLogs:: {} log(s) found in outbox table, sending to kafka", logs.size());
          return Future.join(sendEventLogsToKafka(logs, okapiHeaders))
            .map(logs.stream().map(OutboxEventLog::getEventId).toList())
            .compose(eventIds -> outboxEventLogDAO.deleteEventLogs(conn, eventIds))
            .onSuccess(count -> logger.info("processOutboxEventLogs:: {} log(s) have been deleted from outbox table", count))
            .onFailure(t -> logger.error("processOutboxEventLogs:: Logs deletion failed", t));
        }))
      .onFailure(t -> logger.error("Failed to process outbox event logs", t));
  }

  public Future<Void> saveFundOutboxLog(DBConn conn, Fund fund, FundAuditEvent.Action action) {
    return saveFundOutboxLog(conn, fund, null, action);
  }

  public Future<Void> saveFundOutboxLog(DBConn conn, Fund fund, Fund originalFund, FundAuditEvent.Action action) {
    return saveOutboxLog(conn, action.value(), EntityType.FUND, fund.getId(), AuditEntityWrapper.of(fund, originalFund));
  }

  public Future<Void> saveBudgetOutboxLog(DBConn conn, Budget budget, BudgetAuditEvent.Action action) {
    return saveBudgetOutboxLog(conn, budget, null, action);
  }

  public Future<Void> saveBudgetOutboxLog(DBConn conn, Budget budget, Budget originalBudget, BudgetAuditEvent.Action action) {
    return saveOutboxLog(conn, action.value(), EntityType.BUDGET, budget.getId(), AuditEntityWrapper.of(budget, originalBudget));
  }

  public Future<Void> saveFundOutboxLogs(DBConn conn, List<Fund> funds, List<Fund> originalFunds, FundAuditEvent.Action action) {
    return saveOutboxLogs(conn, action.value(), EntityType.FUND, funds, originalFunds, Fund::getId);
  }

  public Future<Void> saveBudgetOutboxLogs(DBConn conn, List<Budget> budgets, List<Budget> originalBudgets,
                                           BudgetAuditEvent.Action action) {
    return saveOutboxLogs(conn, action.value(), EntityType.BUDGET, budgets, originalBudgets, Budget::getId);
  }

  private <T> Future<Void> saveOutboxLogs(DBConn conn, String action, EntityType entityType, List<T> entities,
                                          List<T> originalEntities, Function<T, String> idGetter) {
    Map<String, T> originalsById = originalEntities.stream().collect(Collectors.toMap(idGetter, Function.identity()));
    var futures = entities.stream()
      .map(entity -> {
        var entityId = idGetter.apply(entity);
        return saveOutboxLog(conn, action, entityType, entityId, AuditEntityWrapper.of(entity, originalsById.get(entityId)));
      })
      .toList();
    return Future.all(futures).mapEmpty();
  }

  private Future<Void> saveOutboxLog(DBConn conn, String action, EntityType entityType, String entityId,
                                     AuditEntityWrapper<?> wrapper) {
    logger.debug("saveOutboxLog:: Saving outbox log for {} with id '{}'", entityType, entityId);
    var eventLog = new OutboxEventLog()
      .withEventId(UUID.randomUUID().toString())
      .withAction(action)
      .withEntityType(entityType)
      .withPayload(Json.encode(wrapper));
    return outboxEventLogDAO.saveEventLog(conn, eventLog)
      .onSuccess(v -> logger.info("saveOutboxLog:: Outbox log has been saved for {} with id '{}'", entityType, entityId))
      .onFailure(t -> logger.warn("saveOutboxLog:: Could not save outbox audit log for {} with id '{}'", entityType, entityId, t));
  }

  /**
   * Sends event logs to Kafka. An event log that cannot be converted to an event (missing entity type, unknown action,
   * malformed payload) is skipped with a warning, so it is deleted together with the sent ones
   * and does not block the processing of the outbox table.
   */
  List<Future<Void>> sendEventLogsToKafka(List<OutboxEventLog> eventLogs, Map<String, String> okapiHeaders) {
    return eventLogs.stream().map(eventLog -> {
      try {
        return sendEventLogToKafka(eventLog, okapiHeaders);
      } catch (RuntimeException e) {
        logger.warn("sendEventLogsToKafka:: Skipping event log '{}' with entity type '{}' and action '{}', reason: {}",
          eventLog.getEventId(), eventLog.getEntityType(), eventLog.getAction(), e.getMessage());
        return Future.<Void>succeededFuture();
      }
    }).toList();
  }

  private Future<Void> sendEventLogToKafka(OutboxEventLog eventLog, Map<String, String> okapiHeaders) {
    if (eventLog.getEntityType() == null) {
      throw new IllegalStateException("Entity type is missing or not supported");
    }
    return switch (eventLog.getEntityType()) {
      case FUND -> {
        var wrapper = decodePayload(eventLog.getPayload(), Fund.class);
        var action = FundAuditEvent.Action.fromValue(eventLog.getAction());
        yield producer.sendFundEvent(wrapper.getEntity(), wrapper.getOriginalEntity(), action, okapiHeaders);
      }
      case BUDGET -> {
        var wrapper = decodePayload(eventLog.getPayload(), Budget.class);
        var action = BudgetAuditEvent.Action.fromValue(eventLog.getAction());
        yield producer.sendBudgetEvent(wrapper.getEntity(), wrapper.getOriginalEntity(), action, okapiHeaders);
      }
    };
  }

  <T> AuditEntityWrapper<T> decodePayload(String payload, Class<T> entityClass) {
    var mapper = DatabindCodec.mapper();
    var wrapperType = mapper.getTypeFactory().constructParametricType(AuditEntityWrapper.class, entityClass);
    AuditEntityWrapper<T> wrapper;
    try {
      wrapper = mapper.readValue(payload, wrapperType);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decode outbox payload for " + entityClass.getSimpleName(), e);
    }
    if (wrapper.getEntity() == null) {
      throw new IllegalStateException("Outbox payload does not contain " + entityClass.getSimpleName());
    }
    return wrapper;
  }

}
