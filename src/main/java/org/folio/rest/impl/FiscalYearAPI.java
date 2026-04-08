package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.util.ResponseUtils.buildErrorResponse;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.FiscalYearCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYears;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYears.GetFinanceStorageFiscalYearsHierarchyByIdResponse;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PgUtil;
import org.folio.service.fiscalyear.FiscalYearService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class FiscalYearAPI implements FinanceStorageFiscalYears {
  public static final String FISCAL_YEAR_TABLE = "fiscal_year";

  @Autowired
  private FiscalYearService fiscalYearService;

  public FiscalYearAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getFinanceStorageFiscalYears(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FISCAL_YEAR_TABLE, FiscalYear.class, FiscalYearCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageFiscalYearsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageFiscalYears(FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FISCAL_YEAR_TABLE, entity, okapiHeaders, vertxContext,PostFinanceStorageFiscalYearsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFiscalYearsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FISCAL_YEAR_TABLE, FiscalYear.class, id, okapiHeaders, vertxContext, GetFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFiscalYearsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FISCAL_YEAR_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFiscalYearsById(String id, FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FISCAL_YEAR_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFiscalYearsHierarchyById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    new DBClient(vertxContext, okapiHeaders)
      .withConn(conn -> fiscalYearService.getFiscalYearHierarchy(id, conn))
      .onComplete(result -> {
        if (result.succeeded()) {
          asyncResultHandler.handle(succeededFuture(
            GetFinanceStorageFiscalYearsHierarchyByIdResponse.respond200WithApplicationJson(result.result())));
        } else {
          asyncResultHandler.handle(buildErrorResponse(result.cause()));
        }
      });
  }

}
