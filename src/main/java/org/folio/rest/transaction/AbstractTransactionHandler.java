package org.folio.rest.transaction;

import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class AbstractTransactionHandler implements TransactionHandler {
  public static final String TRANSACTION_TABLE = "transaction";
  static final String TRANSACTION_LOCATION_PREFIX = "/finance-storage/transactions/";

  protected final Logger log = LoggerFactory.getLogger(this.getClass());

  private final Map<String, String> okapiHeaders;
  private final Handler<AsyncResult<Response>> asyncResultHandler;
  private final Context vertxContext;

  AbstractTransactionHandler(Map<String, String> okapiHeaders, Context ctx, Handler<AsyncResult<Response>> handler) {
    this.okapiHeaders = okapiHeaders;
    this.vertxContext = ctx;
    this.asyncResultHandler = handler;
  }

  @Override
  public void updateTransaction(Transaction transaction) {
    throw new UnsupportedOperationException("Transactions are Immutable");
  }

  String getTenantId() {
    return tenantId(okapiHeaders);
  }

  PostgresClient getPostgresClient() {
    return PostgresClient.getInstance(vertxContext.owner(), getTenantId());
  }

  public Handler<AsyncResult<Response>> getAsyncResultHandler() {
    return asyncResultHandler;
  }

  public Context getVertxContext() {
    return vertxContext;
  }

  public Map<String, String> getOkapiHeaders() {
    return okapiHeaders;
  }
}
