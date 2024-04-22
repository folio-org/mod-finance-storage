package org.folio.rest.persist;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.rest.exception.HttpException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.util.ResponseUtils;

import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Wrapper for RMB's {@link Conn} that calls {@link ResponseUtils#handleFailure(Throwable)}
 * on failure.
 */
public class DBConn {
  private static final Logger logger = LogManager.getLogger();

  private final DBClient dbClient;
  private final Conn conn;

  public DBConn(DBClient dbClient, Conn conn) {
    this.dbClient = dbClient;
    this.conn = conn;
  }

  public <T> Future<Results<T>> get(String table, Class<T> clazz, Criterion filter) {
    return conn.get(table, clazz, filter)
      .recover(ResponseUtils::handleFailure);
  }

  public <T> Future<Results<T>> get(String table, Class<T> clazz, Criterion filter, boolean returnCount) {
    return conn.get(table, clazz, filter, returnCount)
      .recover(ResponseUtils::handleFailure);
  }

  public Future<JsonObject> getById(String table, String id) {
    return conn.getById(table, id)
      .recover(ResponseUtils::handleFailure);
  }

  public <T> Future<T> getById(String table, String id, Class<T> clazz) {
    return conn.getById(table, id, clazz)
      .recover(ResponseUtils::handleFailure);
  }

  public Future<JsonObject> getByIdForUpdate(String table, String id) {
    return conn.getByIdForUpdate(table, id)
      .recover(ResponseUtils::handleFailure);
  }

  public Future<RowSet<Row>> execute(String sql, Tuple params) {
    return conn.execute(sql, params)
        .recover(ResponseUtils::handleFailure);
  }

  public Future<RowSet<Row>> execute(String sql) {
    return conn.execute(sql)
      .recover(ResponseUtils::handleFailure);
  }

  public <T> Future<RowSet<Row>> updateBatch(String table, List<T> entities) {
    return conn.updateBatch(table, entities)
      .recover(ResponseUtils::handleFailure);
  }

  public Future<RowSet<Row>> update(String table, Object entity, String id) {
    return conn.update(table, entity, id)
      .recover(ResponseUtils::handleFailure)
      .map(rowSet -> {
        if (rowSet.rowCount() == 0) {
          logger.error("Entity with id {} not found for update", id);
          throw new HttpException(Response.Status.NOT_FOUND.getStatusCode(), "Entity was not found for update");
        }
        return rowSet;
      });
  }

  public Future<RowSet<Row>> delete(String table, String id) {
    return conn.delete(table, id)
      .recover(ResponseUtils::handleFailure)
      .map(rowSet -> {
        if (rowSet.rowCount() == 0) {
          logger.error("Entity with id {} not found for deletion", id);
          throw new HttpException(Response.Status.NOT_FOUND.getStatusCode(), "Entity was not found for deletion");
        }
        return rowSet;
      });
  }

  public Future<RowSet<Row>> delete(String table, Criterion filter) {
    return conn.delete(table, filter)
      .recover(ResponseUtils::handleFailure);
  }

  public Future<String> save(String table, String id, Object entity) {
    return conn.save(table, id, entity)
      .recover(ResponseUtils::handleFailure);
  }

  public <T> Future<T> saveAndReturnUpdatedEntity(String table, String id, T entity) {
    return conn.saveAndReturnUpdatedEntity(table, id, entity)
      .recover(ResponseUtils::handleFailure);
  }

  public <T> Future<RowSet<Row>> saveBatch(String table, List<T> entities) {
    return conn.saveBatch(table, entities)
      .recover(ResponseUtils::handleFailure);
  }

  public String getTenantId() {
    return dbClient.getTenantId();
  }
}
