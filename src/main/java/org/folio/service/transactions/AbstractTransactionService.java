package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.PgUtil;

public abstract class AbstractTransactionService implements TransactionService {
  public static final String TRANSACTION_TABLE = "transaction";

  public static final String FROM_FUND_ID = "fromFundId";
  public static final String TO_FUND_ID = "toFundId";

  protected final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  public Future<Void> updateTransaction(Transaction transaction, RequestContext requestContext) {
    Promise<Void> promise = Promise.promise();
    PgUtil.put(TRANSACTION_TABLE, transaction, transaction.getId(), requestContext.getHeaders(), requestContext.getContext(), FinanceStorageTransactions.PutFinanceStorageTransactionsByIdResponse.class, event -> {
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
