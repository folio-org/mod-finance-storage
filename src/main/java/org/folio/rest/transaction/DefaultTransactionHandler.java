package org.folio.rest.transaction;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class DefaultTransactionHandler extends AbstractTransactionHandler {

  public DefaultTransactionHandler(Map<String, String> okapiHeaders, Context vertxContext,
      Handler<AsyncResult<Response>> asyncResultHandler) {
    super(okapiHeaders, vertxContext, asyncResultHandler);
  }

  @Override
  public void createTransaction(Transaction transaction) {
    PgUtil.post(TRANSACTION_TABLE, transaction, getOkapiHeaders(), getVertxContext(),
        FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.class, getAsyncResultHandler());
  }

  @Override
  public void updateTransaction(Transaction transaction) {
    PgUtil.put(TRANSACTION_TABLE, transaction, transaction.getId(), getOkapiHeaders(), getVertxContext(),
        FinanceStorageTransactions.PutFinanceStorageTransactionsByIdResponse.class, getAsyncResultHandler());
  }
}
