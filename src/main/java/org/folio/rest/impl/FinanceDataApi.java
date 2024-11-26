package org.folio.rest.impl;


import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.FyFinanceData;
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFinanceData;
import org.folio.rest.persist.PgUtil;
import org.folio.service.financedata.FinanceDataService;

public class FinanceDataApi  implements FinanceStorageFinanceData {

  private static final String FINANCE_DATA_VIEW = "finance_data_view";

  private FinanceDataService financeDataService;

  @Override
  public void getFinanceStorageFinanceData(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FINANCE_DATA_VIEW, FyFinanceData.class, FyFinanceDataCollection.class, query, offset, limit,
      okapiHeaders, vertxContext, GetFinanceStorageFinanceDataResponse.class, asyncResultHandler);
  }

  @Override
  public void putFinanceStorageFinanceData(FyFinanceDataCollection entity, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
      financeDataService.update(entity, vertxContext, okapiHeaders)
        .onComplete(result -> {
          if (result.failed()) {
            asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
              PutFinanceStorageFinanceDataResponse.respond500WithTextPlain(result.cause().getMessage())));
            return;
          }
          asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
            PutFinanceStorageFinanceDataResponse.respond200WithApplicationJson(result.result())));
        });
  }


}
