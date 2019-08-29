package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.EncumbranceCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageEncumbrances;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class EncumbranceAPI implements FinanceStorageEncumbrances {

  private static final String ENCUMBRANCE_TABLE = "encumbrance";

  @Override
  @Validate
  public void getFinanceStorageEncumbrances(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(ENCUMBRANCE_TABLE, org.folio.rest.jaxrs.model.Encumbrance.class, EncumbranceCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetFinanceStorageEncumbrancesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageEncumbrances(String lang, org.folio.rest.jaxrs.model.Encumbrance entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ENCUMBRANCE_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageEncumbrancesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageEncumbrancesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ENCUMBRANCE_TABLE, org.folio.rest.jaxrs.model.Encumbrance.class, id, okapiHeaders, vertxContext, GetFinanceStorageEncumbrancesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageEncumbrancesById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ENCUMBRANCE_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageEncumbrancesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageEncumbrancesById(String id, String lang, org.folio.rest.jaxrs.model.Encumbrance entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ENCUMBRANCE_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageEncumbrancesByIdResponse.class, asyncResultHandler);
  }
}
