package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.resource.Ledger;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class LedgerAPI implements Ledger {
  private static final String LEDGER_TABLE = "ledger";

  private String idFieldName = "id";

  public LedgerAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  public void getLedger(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<org.folio.rest.jaxrs.model.Ledger, LedgerCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(org.folio.rest.jaxrs.model.Ledger.class, LedgerCollection.class, GetLedgerResponse.class);
      QueryHolder cql = new QueryHolder(LEDGER_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  public void postLedger(String lang, org.folio.rest.jaxrs.model.Ledger entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(LEDGER_TABLE, entity, okapiHeaders, vertxContext, PostLedgerResponse.class, asyncResultHandler);
  }

  @Override
  public void getLedgerById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_TABLE, org.folio.rest.jaxrs.model.Ledger.class, id, okapiHeaders, vertxContext, GetLedgerByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteLedgerById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(LEDGER_TABLE, id, okapiHeaders, vertxContext, DeleteLedgerByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putLedgerById(String id, String lang, org.folio.rest.jaxrs.model.Ledger entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(LEDGER_TABLE, entity, id, okapiHeaders, vertxContext, PutLedgerByIdResponse.class, asyncResultHandler);
  }
}
