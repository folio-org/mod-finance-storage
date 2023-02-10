package org.folio.rest.persist;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.util.ResponseUtils;
import java.util.Map;
import java.util.function.Function;

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

  public DBClient(RequestContext requestContext) {
    this(requestContext.getContext(), requestContext.getHeaders());
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

  /**
   * Opens a database connection and starts a transaction on it; use {@link #getConnection()} to get it.
   *
   * {@link #startTx()}, {@link #endTx()} and {@link #rollbackTransaction()} are error-prone
   * because calling {@link #endTx()} or {@link #rollbackTransaction()} can easily be forgotten
   * in some cases so that the database connection stays open forever (resource leak),
   * this may result in reaching PostgreSQL's maximum connection limit.
   *
   * @deprecated use {@link #withTrans(Function)} instead
   */
  @Deprecated
  public Future<DBClient> startTx() {
    Promise<DBClient> promise = Promise.promise();

    pgClient.startTx(connectionAsyncResult -> {
      this.sqlConnection = connectionAsyncResult;
      promise.complete(this);
    });

    return promise.future();
  }

  /**
   * @deprecated use {@link #withTrans(Function)} instead
   */
  @Deprecated
  public Future<Void> endTx() {
    Promise<Void> promise = Promise.promise();
    pgClient.endTx(sqlConnection, asyncResult -> promise.complete());
    return promise.future();
  }

  /**
   * @deprecated use {@link #withTrans(Function)} instead
   */
  @Deprecated
  public Future<Void> rollbackTransaction() {
    Promise<Void> promise = Promise.promise();
    if (sqlConnection.failed()) {
      promise.fail(sqlConnection.cause());
    } else {
      pgClient.rollbackTx(sqlConnection, promise);
    }
    return promise.future();
  }

  public Future<Void> withTrans(Function<DBConn, Future<Void>> function) {
    return pgClient.withConn(conn -> function.apply(new DBConn(this, conn)));
  }

  public Future<Void> save(String table, String id, Object entity) {
    return getPgClient()
        .save(table, id, entity)
        .recover(ResponseUtils::handleFailure)
        .mapEmpty();
  }

  public String getTenantId() {
    return tenantId;
  }

  public Vertx getVertx() {
    return vertx;
  }
}
