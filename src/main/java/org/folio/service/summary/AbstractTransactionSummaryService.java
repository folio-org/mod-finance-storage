package org.folio.service.summary;

import static org.folio.service.transactions.AllOrNothingTransactionService.ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.summary.TransactionSummaryDao;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.HelperUtils;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;

public abstract class AbstractTransactionSummaryService implements TransactionSummaryService {

  private static final Logger logger = LogManager.getLogger(AbstractTransactionSummaryService.class);

  final private TransactionSummaryDao transactionSummaryDao;

  AbstractTransactionSummaryService(TransactionSummaryDao transactionSummaryDao) {
    this.transactionSummaryDao = transactionSummaryDao;
  }

  @Override
  public Future<JsonObject> getAndCheckTransactionSummary(Transaction transaction, DBConn conn) {
    String summaryId = getSummaryId(transaction);
    return this.getTransactionSummary(summaryId, conn)
      .map(summary -> {
        if ((isProcessed(summary))) {
          logger.error("Expected number of transactions for summary with id={} already processed", summary.getString(HelperUtils.ID_FIELD_NAME));
          throw new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED);
        } else {
          logger.info("Successfully retrieved a transaction summary with id {}", summary.getString(HelperUtils.ID_FIELD_NAME));
          return JsonObject.mapFrom(summary);
        }
      });
  }

  @Override
  public Future<JsonObject> getTransactionSummary(String summaryId, DBConn conn) {
    logger.debug("Get summary by id {}", summaryId);
    return transactionSummaryDao.getSummaryById(summaryId, conn);
  }

  @Override
  public Future<JsonObject> getTransactionSummaryWithLocking(String summaryId, DBConn conn) {
    logger.debug("Get summary by id {} with locking", summaryId);
    return transactionSummaryDao.getSummaryByIdWithLocking(summaryId, conn);
  }

  @Override
  public Future<Void> setTransactionsSummariesProcessed(JsonObject summary, DBConn conn) {
    setTransactionsSummariesProcessed(summary);
    return transactionSummaryDao.updateSummary(summary, conn);
  }

  protected abstract boolean isProcessed(JsonObject summary);

  /**
   * Updates summary with negative numbers to highlight that associated transaction list was successfully processed
   *
   * @param summary     processed transaction
   */
  abstract void setTransactionsSummariesProcessed(JsonObject summary);

}
