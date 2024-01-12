package org.folio.rest.persist;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.util.ResponseUtils;
import java.util.Map;
import java.util.function.Function;

public class DBClient {

  final private PostgresClient pgClient;
  final private String tenantId;
  final private Vertx vertx;


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

  public <T> Future<T> withTrans(Function<DBConn, Future<T>> function) {
    return pgClient.withTrans(conn -> function.apply(new DBConn(this, conn)));
  }

  public <T> Future<T> withConn(Function<DBConn, Future<T>> function) {
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
