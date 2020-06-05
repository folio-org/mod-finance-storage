package org.folio.rest.persist;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.folio.rest.tools.utils.TenantTool;

import java.util.Map;

public class DBClient {

  private PostgresClient pgClient;
  private AsyncResult<SQLConnection> sqlConnection;
  private String tenantId;
  private Vertx vertx;


  public DBClient(Context context, Map<String, String> headers) {
    this.pgClient = PgUtil.postgresClient(context, headers);
    this.vertx = context.owner();
    this.tenantId = TenantTool.tenantId(headers);
  }

  public DBClient(Vertx vertx, String tenantId) {
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
    this.vertx = vertx;
    this.tenantId = tenantId;
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

  public DBClient withConnection(AsyncResult<SQLConnection> sqlConnection) {
    this.sqlConnection = sqlConnection;
    return this;
  }

  public Future<DBClient> startTx() {
    Promise<DBClient> promise = Promise.promise();

    pgClient.startTx(connectionAsyncResult -> {
      this.sqlConnection = connectionAsyncResult;
      promise.complete(this);
    });

    return promise.future();
  }

  public Future<Void> endTx() {
    Promise<Void> promise = Promise.promise();
    pgClient.endTx(sqlConnection, asyncResult -> promise.complete());
    return promise.future();
  }

  public Future<Void> rollbackTransaction() {
    Promise<Void> promise = Promise.promise();
    if (sqlConnection.failed()) {
      promise.fail(sqlConnection.cause());
    } else {
      pgClient.rollbackTx(sqlConnection, promise);
    }
    return promise.future();
  }

  public String getTenantId() {
    return tenantId;
  }

  public Vertx getVertx() {
    return vertx;
  }
}
