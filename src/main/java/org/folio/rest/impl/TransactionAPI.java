package org.folio.rest.impl;

import static org.folio.rest.transaction.AbstractTransactionHandler.TRANSACTION_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.TransactionCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.transaction.DefaultTransactionHandler;
import org.folio.rest.transaction.EncumbranceHandler;
import org.folio.rest.transaction.TransactionHandler;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class TransactionAPI implements FinanceStorageTransactions {

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
    getTransactionHandler(transaction, okapiHeaders, vertxContext, asyncResultHandler).createTransaction(transaction);
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
    getTransactionHandler(transaction, okapiHeaders, vertxContext, asyncResultHandler).updateTransaction(transaction);
  }

  private TransactionHandler getTransactionHandler(Transaction transaction, Map<String, String> okapiHeaders, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    if (transaction.getTransactionType() == Transaction.TransactionType.ENCUMBRANCE) {
      return new EncumbranceHandler(okapiHeaders, vertxContext, asyncResultHandler);
    }
    return new DefaultTransactionHandler(okapiHeaders, vertxContext, asyncResultHandler);
  }

}
