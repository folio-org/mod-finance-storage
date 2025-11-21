package org.folio.rest.impl;

import static org.folio.dao.transactions.TransactionTotalPostgresDAO.TRANSACTION_TOTALS_VIEW;
import static org.folio.rest.util.ErrorCodes.INCORRECT_FUND_IDS_PROVIDED;
import static org.folio.rest.util.ResponseUtils.buildErrorResponse;
import static org.folio.rest.util.ResponseUtils.buildOkResponse;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpStatus;
import org.folio.dao.transactions.TransactionTotalDAO;
import org.folio.rest.annotations.Validate;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.TransactionTotal;
import org.folio.rest.jaxrs.model.TransactionTotalBatch;
import org.folio.rest.jaxrs.model.TransactionTotalCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactionTotals;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PgUtil;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import java.util.Map;

public class TransactionTotalApi implements FinanceStorageTransactionTotals {

  @Autowired
  private TransactionTotalDAO transactionTotalDAO;

  public TransactionTotalApi() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

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
    if (toFundIdsPresent && fromFundIdsPresent) {
      var exception = new HttpException(HttpStatus.SC_UNPROCESSABLE_ENTITY, INCORRECT_FUND_IDS_PROVIDED);
      asyncResultHandler.handle(buildErrorResponse(exception));
      return;
    }

    new DBClient(vertxContext, okapiHeaders)
      .withConn(conn -> transactionTotalDAO.getTransactionTotalsBatch(conn, batchRequest, okapiHeaders))
      .onComplete(transactionTotals -> {
        if (transactionTotals.succeeded()) {
          asyncResultHandler.handle(buildOkResponse(transactionTotals.result()));
        } else {
          asyncResultHandler.handle(buildErrorResponse(transactionTotals.cause()));
        }
      });
  }

}
