package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.TransactionPatch;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;

import java.util.List;

public class BatchTransactionService {
  private static final Logger logger = LogManager.getLogger();
  private final DBClientFactory dbClientFactory;

  public BatchTransactionService(DBClientFactory dbClientFactory) {
    this.dbClientFactory = dbClientFactory;
  }

  public Future<Void> processBatch(Batch batch, RequestContext requestContext) {
    try {
      sanityChecks(batch);
    } catch (Exception ex) {
      logger.error(ex);
      return Future.failedFuture(ex);
    }
    DBClient client = dbClientFactory.getDbClient(requestContext);
    return client.withTrans(conn -> createTransactions(batch.getTransactionsToCreate(), conn)
      .compose(v -> updateTransactions(batch.getTransactionsToUpdate(), conn))
      .compose(v -> deleteTransactions(batch.getIdsOfTransactionsToDelete(), conn))
      .compose(v -> patchTransactions(batch.getTransactionPatches(), conn))
    ).onSuccess(v -> logger.info("All batch transaction operations were successful."))
      .onFailure(t -> logger.error("Error when batch processing transactions", t));
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
      if (StringUtils.isBlank(transaction.getId())) {
        throw new HttpException(400, String.format("Id is required in transactions to %s.", operation));
      }
    }
  }

  private Future<Void> createTransactions(List<Transaction> transactions, DBConn conn) {
    if (transactions.isEmpty())
      return Future.succeededFuture();
    return Future.failedFuture(new HttpException(500, "transactionsToCreate: not implemented"));
  }

  private Future<Void> updateTransactions(List<Transaction> transactions, DBConn conn) {
    if (transactions.isEmpty())
      return Future.succeededFuture();
    return Future.failedFuture(new HttpException(500, "transactionsToUpdate: not implemented"));
  }

  private Future<Void> deleteTransactions(List<String> ids, DBConn conn) {
    if (ids.isEmpty())
      return Future.succeededFuture();
    return Future.failedFuture(new HttpException(500, "idsOfTransactionsToDelete: not implemented"));
  }

  private Future<Void> patchTransactions(List<TransactionPatch> patches, DBConn conn) {
    if (patches.isEmpty())
      return Future.succeededFuture();
    return Future.failedFuture(new HttpException(500, "transactionPatches: not implemented"));
  }

}
