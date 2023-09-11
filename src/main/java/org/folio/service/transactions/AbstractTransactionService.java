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

  private static final Logger logger = LogManager.getLogger(AbstractTransactionService.class);

  public static final String TRANSACTION_TABLE = "transaction";
  public static final String FROM_FUND_ID = "fromFundId";
  public static final String TO_FUND_ID = "toFundId";

  @Override
  public Future<Void> updateTransaction(Transaction transaction, RequestContext requestContext) {
    logger.debug("updateTransaction:: Trying to update transaction with id {}", transaction.getId());
    Promise<Void> promise = Promise.promise();
    PgUtil.put(TRANSACTION_TABLE, transaction, transaction.getId(), requestContext.getHeaders(), requestContext.getContext(), FinanceStorageTransactions.PutFinanceStorageTransactionsByIdResponse.class, event -> {
      if (event.succeeded()) {
        if (event.result().getStatus() == 204) {
          logger.info("updateTransaction:: Transaction with id {} successfully updated", transaction.getId());
          promise.complete();
        } else {
          logger.error("updateTransaction:: Updating transaction with id {} failed, status {}", transaction.getId(), event.result().getStatus());
          promise.fail(new HttpException(event.result().getStatus(), event.result().getEntity().toString()));
        }
      } else {
        logger.error("updateTransaction:: Updating transaction with id {} failed", transaction.getId(), event.cause());
        promise.fail(event.cause());
      }
    });
    return promise.future();
  }


}
