package org.folio.dao.audit;

import static org.folio.rest.persist.HelperUtils.getFullTableName;

import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.persist.DBConn;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;

public class AuditOutboxEventLogPostgresDAO implements AuditOutboxEventLogDAO {

  private static final Logger logger = LogManager.getLogger();

  public static final String OUTBOX_TABLE_NAME = "outbox_event_log";

  private static final String EVENT_ID_FIELD = "event_id";
  private static final String ENTITY_TYPE_FIELD = "entity_type";
  private static final String ACTION_FIELD = "action";
  private static final String PAYLOAD_FIELD = "payload";

  private static final String SELECT_SQL = "SELECT * FROM %s FOR UPDATE SKIP LOCKED LIMIT 1000";
  private static final String INSERT_SQL = "INSERT INTO %s (event_id, entity_type, action, payload) VALUES ($1, $2, $3, $4)";
  private static final String BATCH_DELETE_SQL = "DELETE FROM %s WHERE event_id = ANY ($1)";

  @Override
  public Future<List<OutboxEventLog>> getEventLogs(DBConn conn) {
    logger.trace("getEventLogs:: Fetching event logs from outbox table for tenantId '{}'", conn.getTenantId());
    var tableName = getFullTableName(conn.getTenantId(), OUTBOX_TABLE_NAME);
    return conn.execute(SELECT_SQL.formatted(tableName))
      .map(rows -> StreamSupport.stream(rows.spliterator(), false)
        .map(this::convertDbRowToOutboxEventLog)
        .toList())
      .onFailure(t -> logger.warn("getEventLogs:: Failed to fetch event logs for tenantId '{}'", conn.getTenantId(), t));
  }

  @Override
  public Future<Void> saveEventLog(DBConn conn, OutboxEventLog eventLog) {
    logger.debug("saveEventLog:: Saving event log to outbox table with eventId '{}'", eventLog.getEventId());
    var tableName = getFullTableName(conn.getTenantId(), OUTBOX_TABLE_NAME);
    var params = Tuple.of(eventLog.getEventId(), eventLog.getEntityType().value(), eventLog.getAction(), eventLog.getPayload());
    return conn.execute(INSERT_SQL.formatted(tableName), params)
      .onFailure(t -> logger.warn("saveEventLog:: Failed to save event log with id '{}'", eventLog.getEventId(), t))
      .mapEmpty();
  }

  @Override
  public Future<Integer> deleteEventLogs(DBConn conn, List<String> eventIds) {
    logger.debug("deleteEventLogs:: Deleting outbox logs by event ids in batch: '{}'", eventIds);
    var tableName = getFullTableName(conn.getTenantId(), OUTBOX_TABLE_NAME);
    var param = eventIds.stream().map(UUID::fromString).toArray(UUID[]::new);
    return conn.execute(BATCH_DELETE_SQL.formatted(tableName), Tuple.of(param))
      .map(SqlResult::rowCount)
      .onFailure(t -> logger.warn("deleteEventLogs:: Failed to delete event logs by ids: '{}'", eventIds, t));
  }

  private OutboxEventLog convertDbRowToOutboxEventLog(Row row) {
    return new OutboxEventLog()
      .withEventId(row.getUUID(EVENT_ID_FIELD).toString())
      .withEntityType(OutboxEventLog.EntityType.fromValue(row.getString(ENTITY_TYPE_FIELD)))
      .withAction(row.getString(ACTION_FIELD))
      .withPayload(row.getString(PAYLOAD_FIELD));
  }

}
