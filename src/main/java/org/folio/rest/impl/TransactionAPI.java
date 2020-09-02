package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.util.ResponseUtils.buildErrorResponse;
import static org.folio.rest.util.ResponseUtils.buildNoContentResponse;
import static org.folio.rest.util.ResponseUtils.buildResponseWithLocation;
import static org.folio.service.transactions.AbstractTransactionService.TRANSACTION_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.TransactionCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.PgUtil;
import org.folio.service.transactions.TransactionManagingStrategyFactory;
import org.folio.service.transactions.TransactionService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class TransactionAPI implements FinanceStorageTransactions {

  public static final String OKAPI_URL = "X-Okapi-Url";

  @Autowired
  private TransactionManagingStrategyFactory managingServiceFactory;


  public TransactionAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getFinanceStorageTransactions(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(TRANSACTION_TABLE, Transaction.class, TransactionCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageTransactionsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageTransactions(String lang, Transaction transaction, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    getTransactionService(transaction).createTransaction(transaction, new RequestContext(vertxContext, okapiHeaders))
      .onComplete(event -> {
        if (event.succeeded()) {
          asyncResultHandler.handle(succeededFuture(buildResponseWithLocation(okapiHeaders.get(OKAPI_URL), "/finance-storage/transactions", event.result())));
        } else {
          asyncResultHandler.handle(buildErrorResponse(event.cause()));
        }
      });

  }

  @Override
  @Validate
  public void getFinanceStorageTransactionsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(TRANSACTION_TABLE, Transaction.class, id, okapiHeaders, vertxContext, GetFinanceStorageTransactionsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageTransactionsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(TRANSACTION_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageTransactionsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageTransactionsById(String id, String lang, Transaction transaction, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    transaction.setId(id);
    getTransactionService(transaction).updateTransaction(transaction, new RequestContext(vertxContext, okapiHeaders))
    .onComplete(event -> {
      if (event.succeeded()) {
        asyncResultHandler.handle(buildNoContentResponse());
      } else {
        asyncResultHandler.handle(buildErrorResponse(event.cause()));
      }
    });
  }

  private TransactionService getTransactionService(Transaction transaction) {
    return managingServiceFactory.findStrategy(transaction.getTransactionType());
  }


}
