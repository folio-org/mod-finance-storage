package org.folio.dao.summary;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.cql.CQLWrapper;

import javax.ws.rs.core.Response;

import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.service.transactions.AllOrNothingTransactionService.TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION;

public abstract class BaseTransactionSummaryDAO implements TransactionSummaryDao {
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());


  @Override
  public Future<JsonObject> getSummaryById(String summaryId, DBClient client) {
    Promise<JsonObject> promise = Promise.promise();

    logger.debug("Get summary={}", summaryId);
    client.getPgClient().getById(getTableName(), summaryId, reply -> {
      if (reply.failed()) {
        logger.error("Summary retrieval with id={} failed", reply.cause(), summaryId);
        handleFailure(promise, reply);
      } else {
        final JsonObject summary = reply.result();
        if (summary == null) {
          promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION));
        } else {
          logger.debug("Summary with id={} successfully extracted", summaryId);
          promise.complete(summary);
        }
      }
    });
    return promise.future();
  }

  @Override
  public Future<Void> updateSummaryInTransaction(JsonObject summary, DBClient client) {
    Promise<Void> promise = Promise.promise();
    Criterion criterion = new CriterionBuilder().with(ID_FIELD_NAME, summary.getString(ID_FIELD_NAME)).build();
    CQLWrapper cql = new CQLWrapper(criterion);
    client.getPgClient().update(client.getConnection(), getTableName(), summary, cql, false, reply -> {
      if (reply.failed()) {
        logger.error("Summary update with id={} failed", reply.cause(), summary.getString(ID_FIELD_NAME));
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
