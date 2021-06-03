package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.PgUtil;

public class DefaultTransactionService extends AbstractTransactionService {

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, RequestContext requestContext) {
    Promise<Transaction> promise = Promise.promise();
    PgUtil.post(TRANSACTION_TABLE, transaction, requestContext.getHeaders(), requestContext.getContext(), FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.class, event -> {
      if (event.succeeded()) {
        if (event.result().getStatus() == 201) {
          promise.complete((Transaction) event.result().getEntity());
        } else {
          promise.fail(new HttpException(event.result().getStatus(), event.result().getEntity().toString()));
        }
      } else {
        promise.fail(event.cause());
      }
    });
    return promise.future();
  }
}
