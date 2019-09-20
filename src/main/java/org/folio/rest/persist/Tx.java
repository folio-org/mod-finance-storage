package org.folio.rest.persist;

import io.vertx.core.AsyncResult;
import io.vertx.ext.sql.SQLConnection;

public class Tx<T> {

  private T entity;
  private PostgresClient pgClient;
  private AsyncResult<SQLConnection> sqlConnection;

  public Tx(T entity, PostgresClient pgClient) {
    this.entity = entity;
    this.pgClient = pgClient;
  }

  public T getEntity() {
    return entity;
  }

  public PostgresClient getPgClient() {
    return pgClient;
  }

  public AsyncResult<SQLConnection> getConnection() {
    return sqlConnection;
  }

  public void setConnection(AsyncResult<SQLConnection> sqlConnection) {
    this.sqlConnection = sqlConnection;
  }
}
