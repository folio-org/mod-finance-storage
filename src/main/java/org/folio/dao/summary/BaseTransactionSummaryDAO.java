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
  protected final Logger logger = LogManager.getLogger(this.getClass());


  @Override
  public Future<JsonObject> getSummaryById(String summaryId, DBClient client) {
    Promise<JsonObject> promise = Promise.promise();
    client.getPgClient().getById(getTableName(), summaryId, reply -> processGetResult(summaryId, promise, reply));
    return promise.future();
  }

  @Override
  public Future<JsonObject> getSummaryByIdWithLocking(String summaryId, Conn conn) {
    Promise<JsonObject> promise = Promise.promise();
    conn.getByIdForUpdate(getTableName(), summaryId).onComplete(reply -> processGetResult(summaryId, promise, reply));
    return promise.future();
  }

  private void processGetResult(String summaryId, Promise<JsonObject> promise, AsyncResult<JsonObject> reply) {
    if (reply.failed()) {
      logger.error("Summary retrieval with id={} failed", summaryId, reply.cause());
      handleFailure(promise, reply);
    } else {
      final JsonObject summary = reply.result();

      if (summary == null) {
        promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION));
      } else {
        logger.debug("Summary with id={} successfully extracted", summaryId);
        promise.complete(summary);
      }
    }
  }

  @Override
  public Future<Void> updateSummaryInTransaction(JsonObject summary, DBClient client) {
    Promise<Void> promise = Promise.promise();
    Criterion criterion = new CriterionBuilder().with(ID_FIELD_NAME, summary.getString(ID_FIELD_NAME)).build();
    CQLWrapper cql = new CQLWrapper(criterion);
    client.getPgClient().update(client.getConnection(), getTableName(), summary, cql, false, reply -> {
      if (reply.failed()) {
        logger.error("Summary update with id={} failed", summary.getString(ID_FIELD_NAME), reply.cause());
        handleFailure(promise, reply);
      } else {
        logger.debug("Summary with id={} successfully updated", summary.getString(ID_FIELD_NAME));
        promise.complete();
      }
    });
    return promise.future();
  }

  protected abstract String getTableName();

}
