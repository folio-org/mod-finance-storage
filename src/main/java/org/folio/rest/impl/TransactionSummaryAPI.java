package org.folio.rest.impl;

import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.PostgresClient.pojo2json;

import java.util.Collections;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.resource.FinanceStorageOrderTransactionSummaries;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class TransactionSummaryAPI implements FinanceStorageOrderTransactionSummaries {

  private static final Logger log = LoggerFactory.getLogger(TransactionSummaryAPI.class);

  public static final String ORDER_TRANSACTION_SUMMARIES = "order_transaction_summaries";
  public static final String ORDER_TRANSACTION_SUMMARIES_LOCATION_PREFIX = "/finance-storage/order-transaction-summaries/";
  private PostgresClient pgClient;
  private String tenantId;

  public TransactionSummaryAPI(Vertx vertx, String tenantId) {
    this.tenantId = tenantId;
    pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  /**
   * Create {@link OrderTransactionSummary} record if it doesn't already exist for the order, otherwise return existing record.
   *
   * @param summary            how many transactions (encumbrances) to expect for a particular
   *                           order.{@link OrderTransactionSummary#numTransactions} must be greater that 0
   * 
   * @param okapiHeaders
   * @param asyncResultHandler An AsyncResult<Response> Handler {@link Handler} which must be called as follows - Note the
   *                           'GetPatronsResponse' should be replaced with '[nameOfYourFunction]Response': (example only)
   *                           <code>asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetPatronsResponse.withJsonOK( new ObjectMapper().readValue(reply.result().body().toString(), Patron.class))));</code>
   *                           in the final callback (most internal callback) of the function.
   * @param vertxContext       The Vertx Context Object <code>io.vertx.core.Context</code>
   */
  @Override
  @Validate
  public void postFinanceStorageOrderTransactionSummaries(OrderTransactionSummary summary, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (summary.getNumTransactions() < 1) {
      handleValidationError(summary, asyncResultHandler);
    } else {
      String sql = "INSERT INTO " + getFullTableName(tenantId, ORDER_TRANSACTION_SUMMARIES)
          + " (id, jsonb) VALUES (?, ?::JSON) ON CONFLICT (id) DO NOTHING";
      try {
        JsonArray params = new JsonArray();
        params.add(summary.getId());
        params.add(pojo2json(summary));

        pgClient.execute(sql, params, result -> {
          if (result.failed()) {
            String badRequestMessage = PgExceptionUtil.badRequestMessage(result.cause());
            if (badRequestMessage != null) {
              asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageOrderTransactionSummariesResponse
                .respond400WithTextPlain(Response.Status.BAD_REQUEST.getReasonPhrase())));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageOrderTransactionSummariesResponse
                .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
            }
          } else {
            log.debug("Preparing response to client");
            asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageOrderTransactionSummariesResponse
              .respond201WithApplicationJson(summary, PostFinanceStorageOrderTransactionSummariesResponse.headersFor201()
                .withLocation(ORDER_TRANSACTION_SUMMARIES_LOCATION_PREFIX + summary.getId()))));
          }
        });
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageOrderTransactionSummariesResponse
          .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
      }
    }

  }

  private void handleValidationError(OrderTransactionSummary summary, Handler<AsyncResult<Response>> asyncResultHandler) {
    Parameter parameter = new Parameter().withKey("numTransactions")
      .withValue(summary.getNumTransactions()
        .toString());
    Error error = new Error().withCode("-1")
      .withMessage("must be greater than or equal to 1")
      .withParameters(Collections.singletonList(parameter));
    asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageOrderTransactionSummariesResponse
      .respond422WithApplicationJson(new Errors().withErrors(Collections.singletonList(error)))));
  }

  @Override
  @Validate
  public void getFinanceStorageOrderTransactionSummariesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(ORDER_TRANSACTION_SUMMARIES, OrderTransactionSummary.class, id, okapiHeaders, vertxContext,
        GetFinanceStorageOrderTransactionSummariesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageOrderTransactionSummariesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ORDER_TRANSACTION_SUMMARIES, id, okapiHeaders, vertxContext,
        DeleteFinanceStorageOrderTransactionSummariesByIdResponse.class, asyncResultHandler);
  }
}
