package org.folio.dao.summary;

import io.vertx.core.AsyncResult;
import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.service.transactions.AllOrNothingTransactionService.TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION;

import javax.ws.rs.core.Response;

import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.cql.CQLWrapper;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public abstract class BaseTransactionSummaryDAO implements TransactionSummaryDao {

  private static final Logger logger = LogManager.getLogger(BaseTransactionSummaryDAO.class);

  @Override
  public Future<JsonObject> getSummaryById(String summaryId, DBConn conn) {
    logger.debug("Trying to get summary by id {}", summaryId);
    return conn.getById(getTableName(), summaryId)
      .transform(reply -> processGetResult(summaryId, reply));
  }

  @Override
  public Future<JsonObject> getSummaryByIdWithLocking(String summaryId, DBConn conn) {
    logger.debug("Trying to get summary with locking by id {}", summaryId);
    return conn.getByIdForUpdate(getTableName(), summaryId)
      .transform(reply -> processGetResult(summaryId, reply));
  }

  private Future<JsonObject> processGetResult(String summaryId, AsyncResult<JsonObject> reply) {
    if (reply.failed()) {
      logger.error("Summary retrieval with id={} failed", summaryId, reply.cause());
      return Future.future(promise -> handleFailure(promise, reply));
    } else {
      final JsonObject summary = reply.result();

      if (summary == null) {
        logger.warn("Transaction summary with id {} not found for transaction", summaryId, reply.cause());
        return Future.failedFuture(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION));
      } else {
        logger.info("Summary with id {} successfully extracted", summaryId);
        return Future.succeededFuture(summary);
      }
    }
  }

  @Override
  public Future<Void> updateSummary(JsonObject summary, DBConn conn) {
    String id = summary.getString(ID_FIELD_NAME);
    logger.debug("Trying to update summary in transaction by id {}", id);
    return conn.update(getTableName(), summary, id)
      .onSuccess(v -> logger.info("Summary with id {} successfully updated", id))
      .onFailure(e -> logger.error("Summary update with id {} failed", id, e))
      .mapEmpty();
  }

  protected abstract String getTableName();

}
