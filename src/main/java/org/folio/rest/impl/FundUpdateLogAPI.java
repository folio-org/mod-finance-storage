package org.folio.rest.impl;

import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.FundUpdateLog;
import org.folio.rest.jaxrs.model.FundUpdateLogCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFundUpdateLog;
import org.folio.rest.persist.PgUtil;

public class FundUpdateLogAPI implements FinanceStorageFundUpdateLog {

  public static final String FUND_UPDATE_LOG_TABLE = "fund_update_log";

  @Override
  public void getFinanceStorageFundUpdateLog(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FUND_UPDATE_LOG_TABLE, FundUpdateLog.class, FundUpdateLogCollection.class, query, offset, limit,
      okapiHeaders, vertxContext, GetFinanceStorageFundUpdateLogResponse.class, asyncResultHandler);
  }

  @Override
  public void postFinanceStorageFundUpdateLog(FundUpdateLog entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FUND_UPDATE_LOG_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageFundUpdateLogResponse.class, asyncResultHandler);
  }

  @Override
  public void getFinanceStorageFundUpdateLogById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_UPDATE_LOG_TABLE, FundUpdateLog.class, String.valueOf(id), okapiHeaders, vertxContext,
      GetFinanceStorageFundUpdateLogByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteFinanceStorageFundUpdateLogById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_UPDATE_LOG_TABLE, String.valueOf(id), okapiHeaders, vertxContext, DeleteFinanceStorageFundUpdateLogByIdResponse.class,
      asyncResultHandler);
  }

  @Override
  public void putFinanceStorageFundUpdateLogById(String id, FundUpdateLog entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FUND_UPDATE_LOG_TABLE, entity, String.valueOf(id), okapiHeaders, vertxContext, PutFinanceStorageFundUpdateLogByIdResponse.class, asyncResultHandler);
  }
}
