package org.folio.service.summary;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.dao.summary.TransactionSummaryDao;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;

import javax.ws.rs.core.Response;

import static org.folio.service.transactions.AllOrNothingTransactionService.ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED;

public abstract class AbstractTransactionSummaryService implements TransactionSummaryService {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private TransactionSummaryDao transactionSummaryDao;

  AbstractTransactionSummaryService(TransactionSummaryDao transactionSummaryDao) {
    this.transactionSummaryDao = transactionSummaryDao;
  }

  @Override
  public Future<JsonObject> getAndCheckTransactionSummary(Transaction transaction, DBClient client) {
    return this.getTransactionSummary(transaction, client)
      .map(summary -> {
        if ((isProcessed(summary))) {
          logger.debug("Expected number of transactions for summary with id={} already processed", summary.getString(HelperUtils.ID_FIELD_NAME));
          throw new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED);
        } else {
          return JsonObject.mapFrom(summary);
        }
      });
  }

  @Override
  public Future<JsonObject> getTransactionSummary(Transaction transaction, DBClient client) {
    logger.debug("Get summary={}", getSummaryId(transaction));
    String summaryId = getSummaryId(transaction);

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
