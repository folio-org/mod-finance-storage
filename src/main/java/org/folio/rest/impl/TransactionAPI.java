package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.jaxrs.model.TransactionCollection;
import org.folio.rest.jaxrs.resource.Transaction;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class TransactionAPI implements Transaction {
  private static final String TRANSACTION_TABLE = "transaction";

  private String idFieldName = "id";

  public TransactionAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  public void getTransaction(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<org.folio.rest.jaxrs.model.Transaction, TransactionCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(org.folio.rest.jaxrs.model.Transaction.class, TransactionCollection.class, GetTransactionResponse.class);
      QueryHolder cql = new QueryHolder(TRANSACTION_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  public void postTransaction(String lang, org.folio.rest.jaxrs.model.Transaction entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(TRANSACTION_TABLE, entity, okapiHeaders, vertxContext, PostTransactionResponse.class, asyncResultHandler);
  }

  @Override
  public void getTransactionById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(TRANSACTION_TABLE, org.folio.rest.jaxrs.model.Transaction.class, id, okapiHeaders, vertxContext, GetTransactionByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteTransactionById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(TRANSACTION_TABLE, id, okapiHeaders, vertxContext, DeleteTransactionByIdResponse.class, asyncResultHandler);

  }

  @Override
  public void putTransactionById(String id, String lang, org.folio.rest.jaxrs.model.Transaction entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(TRANSACTION_TABLE, entity, id, okapiHeaders, vertxContext, PutTransactionByIdResponse.class, asyncResultHandler);

  }
}
