package org.folio.service.transactions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;

public abstract class AbstractTransactionService implements TransactionService {
  public static final String TRANSACTION_TABLE = "transaction";

  public static final String FROM_FUND_ID = "fromFundId";
  public static final String TO_FUND_ID = "toFundId";

  protected final Logger log = LogManager.getLogger(this.getClass());

  @Override
  public Future<Void> updateTransaction(Transaction transaction, String transactionSummaryId, RequestContext requestContext) {
    Promise<Void> promise = Promise.promise();
    PgUtil.put(TRANSACTION_TABLE, transaction, transaction.getId(), requestContext.getHeaders(), requestContext.getContext(), FinanceStorageTransactions.PutFinanceStorageTransactionsByIdResponse.class, event -> {
      if (event.succeeded()) {
        if (event.result().getStatus() == 204) {
          promise.complete();
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
