package org.folio.rest.service;

import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.handleFailure;
import static org.folio.rest.transaction.AllOrNothingHandler.TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class TransactionSummaryService {

  public static final String IS_PROCESSED = "isProcessed";
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private PostgresClient pgClient;

  public TransactionSummaryService(PostgresClient pgClient) {
    this.pgClient = pgClient;
  }

  public TransactionSummaryService(Context context, Map<String, String> okapiHeaders) {
    this.pgClient = PostgresClient.getInstance(context.owner(),
        TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT)));
  }

  public Future<JsonObject> getSummaryById(String summaryId, String summaryTable) {
    Promise<JsonObject> promise = Promise.promise();

    logger.debug("Get summary={}", summaryId);

    pgClient.getById(summaryTable, summaryId, reply -> {
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


  public boolean isProcessed(JsonObject summary) {
    return summary.getBoolean(IS_PROCESSED);
  }


  public Future<Tx<List<Transaction>>> setTransactionsSummariesProcessed(Tx<List<Transaction>> tx, String summaryId,
      String summaryTable) {
    return getSummaryById(summaryId, summaryTable)
      .compose(summary -> updateOrderTransactionSummary(tx, summary, summaryTable));
  }

  public Future<Tx<List<Transaction>>> updateOrderTransactionSummary(Tx<List<Transaction>> tx, JsonObject summary, String summaryTable) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder().with(ID_FIELD_NAME, summary.getString(ID_FIELD_NAME)).build();
    CQLWrapper cql = new CQLWrapper(criterion);

    summary.put(IS_PROCESSED, true);

    pgClient.update(tx.getConnection(), summaryTable, summary, cql, false, reply -> {
      if (reply.failed()) {
        logger.error("Summary update with id={} failed", reply.cause(), summary.getString(ID_FIELD_NAME));
        handleFailure(promise, reply);
      } else {
        logger.debug("Summary with id={} successfully updated", summary.getString(ID_FIELD_NAME));
        promise.complete(tx);
      }
    });

    return promise.future();
  }

}
