package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Transaction;

import java.util.List;

public class BatchTransactionService {

  public Future<Void> processBatch(Batch batch, RequestContext requestContext) {
    try {
      sanityChecks(batch);
    } catch (Exception ex) {
      return Future.failedFuture(ex);
    }
    if (!batch.getTransactionsToCreate().isEmpty()) {
      return Future.failedFuture(new HttpException(500, "transactionsToCreate: not implemented"));
    }
    if (!batch.getTransactionsToUpdate().isEmpty()) {
      return Future.failedFuture(new HttpException(500, "transactionsToUpdate: not implemented"));
    }
    if (!batch.getIdsOfTransactionsToDelete().isEmpty()) {
      return Future.failedFuture(new HttpException(500, "idsOfTransactionsToDelete: not implemented"));
    }
    if (!batch.getTransactionPatches().isEmpty()) {
      return Future.failedFuture(new HttpException(500, "transactionPatches: not implemented"));
    }
    return Future.succeededFuture();
  }

  private void sanityChecks(Batch batch) {
    checkIdIsPresent(batch.getTransactionsToCreate(), "create");
    checkIdIsPresent(batch.getTransactionsToUpdate(), "update");
    // ids of patches are already checked with the schema
    if (batch.getTransactionsToCreate().isEmpty() && batch.getTransactionsToUpdate().isEmpty() &&
        batch.getIdsOfTransactionsToDelete().isEmpty() && batch.getTransactionPatches().isEmpty()) {
      throw new HttpException(400, "At least one of the batch operations needs to be used.");
    }
  }

  private void checkIdIsPresent(List<Transaction> transactions, String operation) {
    for (Transaction transaction : transactions) {
      if (transaction.getId() == null) {
        throw new HttpException(400, String.format("Id is required in transactions to %s.", operation));
      }
    }
  }
}
