package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FiscalYearCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYear;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class FiscalYearAPI implements FinanceStorageFiscalYear {
  private static final String FISCAL_YEAR_TABLE = "fiscal_year";

  private String idFieldName = "id";

  public FiscalYearAPI(Vertx vertx, String tenantId) {
    PostgresClient.getInstance(vertx, tenantId).setIdField(idFieldName);
  }

  @Override
  @Validate
  public void getFinanceStorageFiscalYear(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<org.folio.rest.jaxrs.model.FiscalYear, FiscalYearCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(org.folio.rest.jaxrs.model.FiscalYear.class, FiscalYearCollection.class, GetFinanceStorageFiscalYearResponse.class);
      QueryHolder cql = new QueryHolder(FISCAL_YEAR_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postFinanceStorageFiscalYear(String lang, org.folio.rest.jaxrs.model.FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FISCAL_YEAR_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageFiscalYearResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFiscalYearById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FISCAL_YEAR_TABLE, org.folio.rest.jaxrs.model.FiscalYear.class, id, okapiHeaders, vertxContext, GetFinanceStorageFiscalYearByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFiscalYearById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FISCAL_YEAR_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageFiscalYearByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFiscalYearById(String id, String lang, org.folio.rest.jaxrs.model.FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FISCAL_YEAR_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageFiscalYearByIdResponse.class, asyncResultHandler);
  }
}
