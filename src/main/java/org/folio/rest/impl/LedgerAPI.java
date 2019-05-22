package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageLedger;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class LedgerAPI implements FinanceStorageLedger {
  private static final String LEDGER_TABLE = "ledger";

  private String idFieldName = "id";

  public LedgerAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  public void getFinanceStorageLedger(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<org.folio.rest.jaxrs.model.Ledger, LedgerCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(org.folio.rest.jaxrs.model.Ledger.class, LedgerCollection.class, GetFinanceStorageLedgerResponse.class);
      QueryHolder cql = new QueryHolder(LEDGER_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postFinanceStorageLedger(String lang, org.folio.rest.jaxrs.model.Ledger entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(LEDGER_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageLedgerResponse.class, asyncResultHandler);
  }

  @Override
  public void getFinanceStorageLedgerById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_TABLE, org.folio.rest.jaxrs.model.Ledger.class, id, okapiHeaders, vertxContext, GetFinanceStorageLedgerByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteFinanceStorageLedgerById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(LEDGER_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageLedgerByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageLedgerById(String id, String lang, org.folio.rest.jaxrs.model.Ledger entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(LEDGER_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageLedgerByIdResponse.class, asyncResultHandler);
  }
}
