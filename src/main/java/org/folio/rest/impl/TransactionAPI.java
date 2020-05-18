package org.folio.rest.impl;

import static org.folio.rest.service.AbstractTransactionService.TRANSACTION_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.TransactionCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.service.DefaultTransactionHandler;
import org.folio.rest.service.PaymentCreditAllOrNothingService;
import org.folio.rest.service.OrderTransactionsService;
import org.folio.rest.service.TransactionService;

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
    getCreateTransactionHandler(transaction, okapiHeaders, vertxContext, asyncResultHandler).createTransaction(transaction, vertxContext, okapiHeaders);
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
    getUpdateTransactionHandler(transaction, okapiHeaders, vertxContext, asyncResultHandler).updateTransaction(transaction, vertxContext, okapiHeaders);
  }

  private TransactionService getCreateTransactionHandler(Transaction transaction, Map<String, String> okapiHeaders, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    if (transaction.getTransactionType() == Transaction.TransactionType.ENCUMBRANCE) {
      return new OrderTransactionsService(okapiHeaders, vertxContext, asyncResultHandler);
    } else if (transaction.getTransactionType() == Transaction.TransactionType.PAYMENT
        || transaction.getTransactionType() == Transaction.TransactionType.CREDIT) {
      return new PaymentCreditAllOrNothingService(okapiHeaders, vertxContext, asyncResultHandler);
    }
    return new DefaultTransactionHandler(okapiHeaders, vertxContext, asyncResultHandler);
  }

  private TransactionService getUpdateTransactionHandler(Transaction transaction, Map<String, String> okapiHeaders, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
    if (transaction.getTransactionType() == Transaction.TransactionType.ENCUMBRANCE) {
      return new PaymentCreditAllOrNothingService(okapiHeaders, vertxContext, asyncResultHandler);
    }
    return new DefaultTransactionHandler(okapiHeaders, vertxContext, asyncResultHandler);
  }

}
