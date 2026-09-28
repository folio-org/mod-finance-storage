package org.folio.dao.audit;

import java.util.List;

import org.folio.rest.jaxrs.model.OutboxEventLog;
import org.folio.rest.persist.DBConn;

import io.vertx.core.Future;

public interface AuditOutboxEventLogDAO {

  Future<List<OutboxEventLog>> getEventLogs(DBConn conn);

  Future<Void> saveEventLog(DBConn conn, OutboxEventLog eventLog);

  Future<Integer> deleteEventLogs(DBConn conn, List<String> eventIds);

}
