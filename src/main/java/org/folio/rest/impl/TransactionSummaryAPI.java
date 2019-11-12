package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.resource.FinanceStorageOrderTransactionSummaries;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class TransactionSummaryAPI implements FinanceStorageOrderTransactionSummaries {
  static final String ORDER_TRANSACTION_SUMMARIES = "order_transaction_summaries";

  @Override
  @Validate
  public void postFinanceStorageOrderTransactionSummaries(OrderTransactionSummary entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(ORDER_TRANSACTION_SUMMARIES, entity, okapiHeaders, vertxContext,
        PostFinanceStorageOrderTransactionSummariesResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageOrderTransactionSummariesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ORDER_TRANSACTION_SUMMARIES, OrderTransactionSummary.class, id, okapiHeaders, vertxContext,
        GetFinanceStorageOrderTransactionSummariesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageOrderTransactionSummariesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ORDER_TRANSACTION_SUMMARIES, id, okapiHeaders, vertxContext,
        DeleteFinanceStorageOrderTransactionSummariesByIdResponse.class, asyncResultHandler);
  }
}
