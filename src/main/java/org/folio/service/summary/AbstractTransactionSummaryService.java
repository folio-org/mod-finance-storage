package org.folio.service.summary;

import static org.folio.service.transactions.AllOrNothingTransactionService.ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.summary.TransactionSummaryDao;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;

public abstract class AbstractTransactionSummaryService implements TransactionSummaryService {

  private final Logger logger = LogManager.getLogger(this.getClass());

  private TransactionSummaryDao transactionSummaryDao;

  AbstractTransactionSummaryService(TransactionSummaryDao transactionSummaryDao) {
    this.transactionSummaryDao = transactionSummaryDao;
  }

  @Override
  public Future<JsonObject> getAndCheckTransactionSummary(String summaryId, DBClient client) {
    return this.getTransactionSummary(summaryId, client)
      .map(summary -> {
        if ((isProcessed(summary))) {
          logger.debug("Expected number of transactions for summary with id={} already processed", summary.getString(HelperUtils.ID_FIELD_NAME));
          throw new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED);
        } else {
          return JsonObject.mapFrom(summary);
        }
      });
  }

  @Override
  public Future<JsonObject> getTransactionSummary(String summaryId, DBClient client) {
    logger.debug("Get summary={}", summaryId);

    return transactionSummaryDao.getSummaryById(summaryId, client);
  }

  @Override
  public Future<Void> setTransactionsSummariesProcessed(JsonObject summary, DBClient client) {
    setTransactionsSummariesProcessed(summary);
    return transactionSummaryDao.updateSummaryInTransaction(summary, client);
  }

  protected abstract boolean isProcessed(JsonObject summary);

  /**
   * Updates summary with negative numbers to highlight that associated transaction list was successfully processed
   *
   * @param summary     processed transaction
   */
  abstract void setTransactionsSummariesProcessed(JsonObject summary);

}
