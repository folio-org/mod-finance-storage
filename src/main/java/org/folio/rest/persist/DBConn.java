package org.folio.rest.persist;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.folio.rest.util.ResponseUtils;

/**
 * Wrapper for RMB's {@link Conn} that calls {@link ResponseUtils#handleFailure(Throwable)}
 * on failure.
 */
public class DBConn {
  private final DBClient dbClient;
  private final Conn conn;

  public DBConn(DBClient dbClient, Conn conn) {
    this.dbClient = dbClient;
    this.conn = conn;
  }

  public Future<RowSet<Row>> execute(String sql, Tuple params) {
    return conn.execute(sql, params)
        .recover(ResponseUtils::handleFailure);
  }

  public Future<RowSet<Row>> update(String table, Object entity, String id) {
    return conn.update(table, entity, id)
        .recover(ResponseUtils::handleFailure);
  }

  public String getTenantId() {
    return dbClient.getTenantId();
  }
}
