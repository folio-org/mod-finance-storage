package org.folio.dao.summary;

import io.vertx.core.AsyncResult;
import org.folio.rest.persist.Conn;
import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.service.transactions.AllOrNothingTransactionService.TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION;

import javax.ws.rs.core.Response;

import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLWrapper;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public abstract class BaseTransactionSummaryDAO implements TransactionSummaryDao {

  private static final Logger logger = LogManager.getLogger(BaseTransactionSummaryDAO.class);

  @Override
  public Future<JsonObject> getSummaryById(String summaryId, DBClient client) {
    logger.debug("Trying to get summary by id {}", summaryId);
    return client.getPgClient().getById(getTableName(), summaryId)
      .transform(reply -> processGetResult(summaryId, reply));
  }

  @Override
  public Future<JsonObject> getSummaryByIdWithLocking(String summaryId, Conn conn) {
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
  public Future<Void> updateSummaryInTransaction(JsonObject summary, DBClient client) {
    logger.debug("Trying to update summary in transaction by id {}", summary.getString(ID_FIELD_NAME));
    Promise<Void> promise = Promise.promise();
    Criterion criterion = new CriterionBuilder().with(ID_FIELD_NAME, summary.getString(ID_FIELD_NAME)).build();
    CQLWrapper cql = new CQLWrapper(criterion);
    client.getPgClient().update(client.getConnection(), getTableName(), summary, cql, false, reply -> {
      if (reply.failed()) {
        logger.error("Summary update with id {} failed", summary.getString(ID_FIELD_NAME), reply.cause());
        handleFailure(promise, reply);
      } else {
        logger.info("Summary with id {} successfully updated", summary.getString(ID_FIELD_NAME));
        promise.complete();
      }
    });
    return promise.future();
  }

  protected abstract String getTableName();

}
