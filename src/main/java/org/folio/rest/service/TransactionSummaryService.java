package org.folio.rest.service;

import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.service.AllOrNothingTransactionService.TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION;
import static org.folio.rest.util.ResponseUtils.handleFailure;

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

  public static final String NUM_ENCUMBRANCES = "numEncumbrances";
  public static final String NUM_TRANSACTIONS = "numTransactions";
  public static final String NUM_PAYMENTS_CREDITS = "numPaymentsCredits";
  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private PostgresClient pgClient;

  public TransactionSummaryService(PostgresClient pgClient) {
    this.pgClient = pgClient;
  }

  public TransactionSummaryService(Context context, Map<String, String> okapiHeaders) {
    this.pgClient = PostgresClient.getInstance(context.owner(),
        TenantTool.calculateTenantId(okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT)));
  }


  public Future<JsonObject> getAndCheckTransactionSummary(Transaction transaction) {
    logger.debug("Get summary={}", getSummaryId(transaction));
    String summaryId = getSummaryId(transaction);
    return transactionSummaryService.getSummaryById(summaryId, summaryTable)
      .map(summary -> {
        if ((isProcessed(summary, transaction))) {
          log.debug("Expected number of transactions for summary with id={} already processed", summary.getString(ID_FIELD_NAME));
          throw new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED);
        }
        return summary;
      });
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


  public boolean isProcessed(JsonObject summary, Transaction transaction) {
    if (Transaction.TransactionType.ENCUMBRANCE == transaction.getTransactionType()) {
      if (summary.getInteger(NUM_ENCUMBRANCES) != null) {
        return summary.getInteger(NUM_ENCUMBRANCES) < 0;
      }
      return summary.getInteger(NUM_TRANSACTIONS) < 0;
    }

    return summary.getInteger(NUM_PAYMENTS_CREDITS) < 0;

  }


  public Future<Tx<List<Transaction>>> setTransactionsSummariesProcessed(Tx<List<Transaction>> tx, JsonObject summary,
      String summaryTable) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder().with(ID_FIELD_NAME, summary.getString(ID_FIELD_NAME)).build();
    CQLWrapper cql = new CQLWrapper(criterion);

    setTransactionsSummariesProcessed(summary, tx.getEntity().get(0));

    tx.getPgClient().update(tx.getConnection(), summaryTable, summary, cql, false, reply -> {
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

  /**
   * Updates summary with negative numbers to highlight that associated transaction list was successfully processed
   *
   * @param summary     processed transaction
   * @param transaction one of the processed transactions
   */
  private void setTransactionsSummariesProcessed(JsonObject summary, Transaction transaction) {
    if (Transaction.TransactionType.ENCUMBRANCE == transaction.getTransactionType()) {
      if (summary.getInteger(NUM_ENCUMBRANCES) != null) {
        summary.put(NUM_ENCUMBRANCES, -summary.getInteger(NUM_ENCUMBRANCES));
      } else {
        summary.put(NUM_TRANSACTIONS, -summary.getInteger(NUM_TRANSACTIONS));
      }
    } else {
      summary.put(NUM_PAYMENTS_CREDITS, -summary.getInteger(NUM_PAYMENTS_CREDITS));
    }
  }

}
