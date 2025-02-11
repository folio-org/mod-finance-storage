package org.folio.rest.impl;


import static org.folio.rest.util.ResponseUtils.buildErrorResponse;
import static org.folio.rest.util.ResponseUtils.buildNoContentResponse;

import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.FyFinanceData;
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFinanceData;
import org.folio.rest.persist.PgUtil;
import org.folio.service.financedata.FinanceDataService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class FinanceDataApi  implements FinanceStorageFinanceData {

  private static final String FINANCE_DATA_VIEW = "finance_data_view";

  @Autowired
  private FinanceDataService financeDataService;

  public FinanceDataApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  public void getFinanceStorageFinanceData(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FINANCE_DATA_VIEW, FyFinanceData.class, FyFinanceDataCollection.class, query, offset, limit,
      okapiHeaders, vertxContext, GetFinanceStorageFinanceDataResponse.class, asyncResultHandler);
  }

  @Override
  public void putFinanceStorageFinanceData(FyFinanceDataCollection entity, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
      financeDataService.update(entity, new RequestContext(vertxContext, okapiHeaders))
        .onComplete(event -> {
          if (event.succeeded()) {
            asyncResultHandler.handle(buildNoContentResponse());
          } else {
            asyncResultHandler.handle(buildErrorResponse(event.cause()));
          }
        });
  }
}
