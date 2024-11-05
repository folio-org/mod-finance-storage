package org.folio.rest.impl;

import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FundUpdateLog;
import org.folio.rest.jaxrs.model.FundUpdateLogCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFundUpdateLogs;
import org.folio.rest.persist.PgUtil;

public class FundUpdateLogAPI implements FinanceStorageFundUpdateLogs {

  public static final String FUND_UPDATE_LOG_TABLE = "fund_update_log";

  @Override
  @Validate
  public void getFinanceStorageFundUpdateLogs(String query, String totalRecords, int offset, int limit,
                                             Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                             Context vertxContext) {
    PgUtil.get(FUND_UPDATE_LOG_TABLE, FundUpdateLog.class, FundUpdateLogCollection.class, query, offset, limit,
      okapiHeaders, vertxContext, GetFinanceStorageFundUpdateLogsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageFundUpdateLogs(FundUpdateLog entity, Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(FUND_UPDATE_LOG_TABLE, entity, okapiHeaders, vertxContext,
      PostFinanceStorageFundUpdateLogsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageFundUpdateLogsById(String id, Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FUND_UPDATE_LOG_TABLE, FundUpdateLog.class, String.valueOf(id), okapiHeaders, vertxContext,
      GetFinanceStorageFundUpdateLogsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFundUpdateLogsById(String id, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(FUND_UPDATE_LOG_TABLE, String.valueOf(id), okapiHeaders, vertxContext,
      DeleteFinanceStorageFundUpdateLogsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageFundUpdateLogsById(String id, FundUpdateLog entity, Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FUND_UPDATE_LOG_TABLE, entity, String.valueOf(id), okapiHeaders, vertxContext,
      PutFinanceStorageFundUpdateLogsByIdResponse.class, asyncResultHandler);
  }
}
