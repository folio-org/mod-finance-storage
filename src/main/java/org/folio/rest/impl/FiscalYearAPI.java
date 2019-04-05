package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.jaxrs.model.FiscalYearCollection;
import org.folio.rest.jaxrs.resource.FiscalYear;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class FiscalYearAPI implements FiscalYear {
  private static final String FISCAL_YEAR_TABLE = "fiscal_year";
  private static final String FY_LOCATION_PREFIX = "/fiscal_year/";

  private String idFieldName = "id";

  public FiscalYearAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  public void getFiscalYear(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<org.folio.rest.jaxrs.model.FiscalYear, FiscalYearCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(org.folio.rest.jaxrs.model.FiscalYear.class, FiscalYearCollection.class, GetFiscalYearResponse.class);
      QueryHolder cql = new QueryHolder(FISCAL_YEAR_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  public void postFiscalYear(String lang, org.folio.rest.jaxrs.model.FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FISCAL_YEAR_TABLE, entity, okapiHeaders, vertxContext, PostFiscalYearResponse.class, asyncResultHandler);
  }

  @Override
  public void getFiscalYearById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FISCAL_YEAR_TABLE, org.folio.rest.jaxrs.model.FiscalYear.class, id, okapiHeaders, vertxContext, GetFiscalYearByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteFiscalYearById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FISCAL_YEAR_TABLE, id, okapiHeaders, vertxContext, DeleteFiscalYearByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putFiscalYearById(String id, String lang, org.folio.rest.jaxrs.model.FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FISCAL_YEAR_TABLE, entity, id, okapiHeaders, vertxContext, PutFiscalYearByIdResponse.class, asyncResultHandler);
  }
}
