package org.folio.rest.impl;

import static org.folio.rest.util.ResponseUtils.buildErrorResponse;
import static org.folio.rest.util.ResponseUtils.buildNoContentResponse;
import static org.folio.dao.transactions.BatchTransactionDAO.TRANSACTIONS_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.TransactionCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.PgUtil;
import org.folio.service.transactions.batch.BatchTransactionService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class TransactionAPI implements FinanceStorageTransactions {

  @Autowired
  private BatchTransactionService batchTransactionService;


  public TransactionAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getFinanceStorageTransactions(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(TRANSACTIONS_TABLE, Transaction.class, TransactionCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageTransactionsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageTransactionsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(TRANSACTIONS_TABLE, Transaction.class, id, okapiHeaders, vertxContext, GetFinanceStorageTransactionsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageTransactionsBatchAllOrNothing(Batch batch, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    batchTransactionService.processBatch(batch, new RequestContext(vertxContext, okapiHeaders))
      .onComplete(event -> {
        if (event.succeeded()) {
          asyncResultHandler.handle(buildNoContentResponse());
        } else {
          asyncResultHandler.handle(buildErrorResponse(event.cause()));
        }
      });
  }

}
