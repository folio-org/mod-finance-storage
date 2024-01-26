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
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.TransactionCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.PgUtil;
import org.folio.service.transactions.BatchTransactionService;
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
  @Autowired
  private BatchTransactionService batchTransactionService;
  @Autowired
  private DBClientFactory dbClientFactory;


  public TransactionAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getFinanceStorageTransactions(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(TRANSACTION_TABLE, Transaction.class, TransactionCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageTransactionsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageTransactions(Transaction transaction, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DBClient client = dbClientFactory.getDbClient(new RequestContext(vertxContext, okapiHeaders));
    client.withTrans(conn -> getTransactionService(transaction).createTransaction(transaction, conn))
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
  public void getFinanceStorageTransactionsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(TRANSACTION_TABLE, Transaction.class, id, okapiHeaders, vertxContext, GetFinanceStorageTransactionsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageTransactionsById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(TRANSACTION_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageTransactionsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageTransactionsById(String id, Transaction transaction, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    transaction.setId(id);
    DBClient client = dbClientFactory.getDbClient(new RequestContext(vertxContext, okapiHeaders));
    client.withTrans(conn -> getTransactionService(transaction).updateTransaction(transaction, conn))
      .onComplete(event -> {
        if (event.succeeded()) {
          asyncResultHandler.handle(buildNoContentResponse());
        } else {
          asyncResultHandler.handle(buildErrorResponse(event.cause()));
        }
      });
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

  private TransactionService getTransactionService(Transaction transaction) {
    return managingServiceFactory.findStrategy(transaction.getTransactionType());
  }

}
