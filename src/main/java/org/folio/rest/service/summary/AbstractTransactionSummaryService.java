package org.folio.rest.service.summary;

import static org.folio.rest.service.transactions.AllOrNothingTransactionService.ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.dao.summary.TransactionSummaryDao;
import org.folio.rest.jaxrs.model.Entity;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Context;
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
  public Future<T> getAndCheckTransactionSummary(Transaction transaction, Context context, Map<String, String> okapiHeaders) {
    logger.debug("Get summary={}", getSummaryId(transaction));
    String summaryId = getSummaryId(transaction);
    DBClient client = new DBClient(context, okapiHeaders);
    return transactionSummaryDao.getSummaryById(summaryId, client)
      .map(summary -> {
        if ((isProcessed(summary))) {
          logger.debug("Expected number of transactions for summary with id={} already processed", summary.getId());
          throw new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED);
        }
        return summary;
      });
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
