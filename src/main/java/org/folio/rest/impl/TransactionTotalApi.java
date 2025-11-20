package org.folio.rest.impl;

import static org.folio.rest.persist.HelperUtils.convertFieldListToCqlQuery;
import static org.folio.rest.util.ResponseUtils.buildErrorResponse;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.annotations.Validate;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.TransactionTotal;
import org.folio.rest.jaxrs.model.TransactionTotalBatch;
import org.folio.rest.jaxrs.model.TransactionTotalCollection;
import org.folio.rest.jaxrs.model.TransactionType;
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

  @Override
  public void postFinanceStorageTransactionTotalsBatch(TransactionTotalBatch batchRequest, Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    String fundQuery;
    if (CollectionUtils.isNotEmpty(batchRequest.getToFundIds())) {
      fundQuery = convertFieldListToCqlQuery(batchRequest.getToFundIds(), "toFundId", true);
    } else if (CollectionUtils.isNotEmpty(batchRequest.getFromFundIds())) {
      fundQuery = convertFieldListToCqlQuery(batchRequest.getFromFundIds(), "fromFundId", true);
    } else {
      var exception = new HttpException(HttpStatus.SC_BAD_REQUEST, "At least one of 'toFundIds' or 'fromFundIds' must be provided");
      asyncResultHandler.handle(buildErrorResponse(exception));
      return;
    }

    var trTypeValues = batchRequest.getTransactionTypes().stream().map(TransactionType::value).toList();
    var trTypeQuery = convertFieldListToCqlQuery(trTypeValues, "transactionType", true);

    var query = String.format("(fiscalYearId==%s AND %s) AND %s", batchRequest.getFiscalYearId(), trTypeQuery, fundQuery);

    PgUtil.get(TRANSACTION_TOTALS_VIEW, TransactionTotal.class, TransactionTotalCollection.class, query, 0, Integer.MAX_VALUE, okapiHeaders, vertxContext,
      FinanceStorageTransactionTotals.PostFinanceStorageTransactionTotalsBatchResponse.class, asyncResultHandler);
  }

}
