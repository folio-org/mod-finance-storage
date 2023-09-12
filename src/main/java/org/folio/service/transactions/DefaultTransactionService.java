package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.PgUtil;

public class DefaultTransactionService extends AbstractTransactionService {

  private static final Logger logger = LogManager.getLogger(DefaultTransactionService.class);

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, RequestContext requestContext) {
    logger.debug("createTransaction:: Trying to create transaction");
    Promise<Transaction> promise = Promise.promise();
    PgUtil.post(TRANSACTION_TABLE, transaction, requestContext.getHeaders(), requestContext.getContext(), FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.class, event -> {
      if (event.succeeded()) {
        if (event.result().getStatus() == 201) {
          Transaction createdTransaction = (Transaction) event.result().getEntity();
          logger.info("createTransaction:: Transaction with id {} successfully created", createdTransaction.getId());
          promise.complete(createdTransaction);
        } else {
          logger.error("createTransaction:: Creating transaction  with id {} failed with status {}", transaction.getId(), event.result().getStatus());
          promise.fail(new HttpException(event.result().getStatus(), event.result().getEntity().toString()));
        }
      } else {
        logger.error("createTransaction:: Creating transaction  with id {} failed", transaction.getId(), event.cause());
        promise.fail(event.cause());
      }
    });
    return promise.future();
  }
}
