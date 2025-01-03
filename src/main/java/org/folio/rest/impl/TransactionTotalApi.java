package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TransactionTotal;
import org.folio.rest.jaxrs.model.TransactionTotalCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactionTotals;
import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;

public class TransactionTotalApi implements FinanceStorageTransactionTotals {

  public static final String TRANSACTION_TOTALS_VIEW = "transaction_totals_view";

  @Override
  @Validate
  public void getFinanceStorageTransactionTotals(String query, String totalRecords, int offset, int limit,
                                                 Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(TRANSACTION_TOTALS_VIEW, TransactionTotal.class, TransactionTotalCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      FinanceStorageTransactionTotals.GetFinanceStorageTransactionTotalsResponse.class, asyncResultHandler);
  }
}
