package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FiscalYearCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYears;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.QueryHolder;

import javax.ws.rs.core.Response;
import java.util.Map;

import static org.folio.rest.persist.HelperUtils.getEntitiesCollection;

public class FiscalYearAPI implements FinanceStorageFiscalYears {
  private static final String FISCAL_YEAR_TABLE = "fiscal_year";

  @Override
  @Validate
  public void getFinanceStorageFiscalYears(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    vertxContext.runOnContext((Void v) -> {
      EntitiesMetadataHolder<org.folio.rest.jaxrs.model.FiscalYear, FiscalYearCollection> entitiesMetadataHolder = new EntitiesMetadataHolder<>(org.folio.rest.jaxrs.model.FiscalYear.class, FiscalYearCollection.class, GetFinanceStorageFiscalYearsResponse.class);
      QueryHolder cql = new QueryHolder(FISCAL_YEAR_TABLE, query, offset, limit, lang);
      getEntitiesCollection(entitiesMetadataHolder, cql, asyncResultHandler, vertxContext, okapiHeaders);
    });
  }

  @Override
  @Validate
  public void postFinanceStorageFiscalYears(String lang, org.folio.rest.jaxrs.model.FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FISCAL_YEAR_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageFiscalYearsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFiscalYearsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FISCAL_YEAR_TABLE, org.folio.rest.jaxrs.model.FiscalYear.class, id, okapiHeaders, vertxContext, GetFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFiscalYearsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FISCAL_YEAR_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFiscalYearsById(String id, String lang, org.folio.rest.jaxrs.model.FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FISCAL_YEAR_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }
}
