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
    var toFundIdsPresent = CollectionUtils.isNotEmpty(batchRequest.getToFundIds());
    var fromFundIdsPresent = CollectionUtils.isNotEmpty(batchRequest.getFromFundIds());
    if (!toFundIdsPresent && !fromFundIdsPresent || toFundIdsPresent && fromFundIdsPresent) {
      var exception = new HttpException(HttpStatus.SC_UNPROCESSABLE_ENTITY, "Either 'toFundIds' or 'fromFundIds' must be provided, but not both");
      asyncResultHandler.handle(buildErrorResponse(exception));
      return;
    }

    String fundQuery = toFundIdsPresent
      ? convertFieldListToCqlQuery(batchRequest.getToFundIds(), "toFundId", true)
      : convertFieldListToCqlQuery(batchRequest.getFromFundIds(), "fromFundId", true);
    var trTypeValues = batchRequest.getTransactionTypes().stream().map(TransactionType::value).toList();
    var trTypeQuery = convertFieldListToCqlQuery(trTypeValues, "transactionType", true);

    var query = "(fiscalYearId==%s AND %s) AND %s".formatted(batchRequest.getFiscalYearId(), trTypeQuery, fundQuery);

    PgUtil.get(TRANSACTION_TOTALS_VIEW, TransactionTotal.class, TransactionTotalCollection.class, query, 0, Integer.MAX_VALUE, okapiHeaders, vertxContext,
      FinanceStorageTransactionTotals.PostFinanceStorageTransactionTotalsBatchResponse.class, asyncResultHandler);
  }

}
