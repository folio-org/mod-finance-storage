package org.folio.service.transactions;

import java.util.Map;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class DefaultTransactionService extends AbstractTransactionService {

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, Context context, Map<String, String> headers) {
    Promise<Transaction> promise = Promise.promise();
    PgUtil.post(TRANSACTION_TABLE, transaction, headers, context, FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.class, event -> {
      if (event.succeeded()) {
        if (event.result().getStatus() == 201) {
          promise.complete((Transaction) event.result().getEntity());
        } else {
          promise.fail(new HttpStatusException(event.result().getStatus(), event.result().getEntity().toString()));
        }
      } else {
        promise.fail(event.cause());
      }
    });
    return promise.future();
  }
}
