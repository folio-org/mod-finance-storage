package org.folio.service.summary;

import static org.folio.service.transactions.BaseAllOrNothingTransactionService.ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED;

import javax.ws.rs.core.Response;

import org.folio.dao.summary.TransactionSummaryDao;
import org.folio.rest.jaxrs.model.Entity;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public abstract class AbstractTransactionSummaryService<T extends Entity> implements TransactionSummaryService<T>{

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private TransactionSummaryDao<T> transactionSummaryDao;

  AbstractTransactionSummaryService(TransactionSummaryDao<T> transactionSummaryDao) {
    this.transactionSummaryDao = transactionSummaryDao;
  }

  @Override
  public Future<T> getAndCheckTransactionSummary(Transaction transaction, DBClient client) {
    return this.getTransactionSummary(transaction, client)
      .map(summary -> {
        if ((isProcessed(summary))) {
          logger.debug("Expected number of transactions for summary with id={} already processed", summary.getId());
          throw new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED);
        } else {
          return summary;
        }
      });
  }

  @Override
  public Future<T> getTransactionSummary(Transaction transaction, DBClient client) {
    logger.debug("Get summary={}", getSummaryId(transaction));
    String summaryId = getSummaryId(transaction);

    return transactionSummaryDao.getSummaryById(summaryId, client);
  }

  @Override
  public Future<Void> setTransactionsSummariesProcessed(T summary, DBClient client) {
    setTransactionsSummariesProcessed(summary);
    return transactionSummaryDao.updateSummaryInTransaction(summary, client);
  }

  protected abstract String getSummaryId(Transaction transaction);

  protected abstract boolean isProcessed(T summary);

  /**
   * Updates summary with negative numbers to highlight that associated transaction list was successfully processed
   *
   * @param summary     processed transaction
   */
  abstract void setTransactionsSummariesProcessed(T summary);

}
