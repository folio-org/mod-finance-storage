package org.folio.service.transactions;

import java.util.Map;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public abstract class AbstractTransactionService implements TransactionService {
  public static final String TRANSACTION_TABLE = "transaction";

  public static final String FROM_FUND_ID = "fromFundId";
  public static final String TO_FUND_ID = "toFundId";

  protected final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  public Future<Void> updateTransaction(String id, Transaction transaction, Context context, Map<String, String> headers) {
    Promise<Void> promise = Promise.promise();
    PgUtil.put(TRANSACTION_TABLE, transaction, id, headers, context, FinanceStorageTransactions.PutFinanceStorageTransactionsByIdResponse.class, event -> {
      if (event.succeeded()) {
        if (event.result().getStatus() == 204) {
          promise.complete();
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
