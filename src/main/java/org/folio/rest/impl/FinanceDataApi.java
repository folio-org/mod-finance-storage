package org.folio.rest.impl;


import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.FyAllFinanceDatum;
import org.folio.rest.jaxrs.model.FyFinanceDataCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageFinanceData;
import org.folio.rest.persist.PgUtil;
import org.folio.service.financedata.FinanceDataService;
import org.springframework.beans.factory.annotation.Autowired;

public class FinanceDataApi  implements FinanceStorageFinanceData {
  private static final String FINANCE_DATA_VIEW = "finance_data_view";

  @Autowired
  private FinanceDataService financeDataService;

  @Override
  public void getFinanceStorageFinanceData(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FINANCE_DATA_VIEW, FyAllFinanceDatum.class, FyFinanceDataCollection.class, query, offset, limit,
      okapiHeaders, vertxContext, GetFinanceStorageFinanceDataResponse.class, asyncResultHandler);
  }
}
