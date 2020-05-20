package org.folio.rest.dao.summary;

import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.service.transactions.AllOrNothingTransactionService.TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Entity;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLWrapper;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public abstract class BaseTransactionSummaryDAO<T extends Entity> implements TransactionSummaryDao<T> {
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());


  @Override
  public Future<T> getSummaryById(String summaryId, DBClient client) {
    Promise<T> promise = Promise.promise();

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
          promise.complete(summary.mapTo(getClazz()));
        }
      }
    });
    return promise.future();
  }

  @Override
  public Future<Void> updateSummaryInTransaction(T summary, DBClient client) {
    Promise<Void> promise = Promise.promise();
    Criterion criterion = new CriterionBuilder().with(ID_FIELD_NAME, summary.getId()).build();
    CQLWrapper cql = new CQLWrapper(criterion);
    client.getPgClient().update(client.getConnection(), getTableName(), summary, cql, false, reply -> {
      if (reply.failed()) {
        logger.error("Summary update with id={} failed", reply.cause(), summary.getId());
        handleFailure(promise, reply);
      } else {
        logger.debug("Summary with id={} successfully updated", summary.getId());
        promise.complete();
      }
    });
    return promise.future();
  }

  protected abstract String getTableName();

  protected abstract Class<T> getClazz();
}
