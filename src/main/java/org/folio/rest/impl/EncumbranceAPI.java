package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.EncumbranceCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageEncumbrance;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class EncumbranceAPI implements FinanceStorageEncumbrance {

  private static final String ENCUMBRANCE_TABLE = "encumbrance";
  private String idFieldName = "id";

  public EncumbranceAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getFinanceStorageEncumbrance(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<org.folio.rest.jaxrs.model.Encumbrance, EncumbranceCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(org.folio.rest.jaxrs.model.Encumbrance.class, EncumbranceCollection.class, GetFinanceStorageEncumbranceResponse.class);
      QueryHolder cql = new QueryHolder(ENCUMBRANCE_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postFinanceStorageEncumbrance(String lang, org.folio.rest.jaxrs.model.Encumbrance entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ENCUMBRANCE_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageEncumbranceResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageEncumbranceById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ENCUMBRANCE_TABLE, org.folio.rest.jaxrs.model.Encumbrance.class, id, okapiHeaders, vertxContext, GetFinanceStorageEncumbranceByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageEncumbranceById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ENCUMBRANCE_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageEncumbranceByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageEncumbranceById(String id, String lang, org.folio.rest.jaxrs.model.Encumbrance entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ENCUMBRANCE_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageEncumbranceByIdResponse.class, asyncResultHandler);
  }
}
