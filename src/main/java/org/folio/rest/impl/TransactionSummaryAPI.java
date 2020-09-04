package org.folio.rest.impl;

import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.PostgresClient.pojo2JsonObject;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.OrderTransactionSummary;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.resource.FinanceStorage;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.sqlclient.Tuple;

public class TransactionSummaryAPI implements FinanceStorage {

  public static final String ORDER_TRANSACTION_SUMMARIES = "order_transaction_summaries";
  public static final String ORDER_TRANSACTION_SUMMARIES_LOCATION_PREFIX = "/finance-storage/order-transaction-summaries/";
  public static final String INVOICE_TRANSACTION_SUMMARIES = "invoice_transaction_summaries";
  public static final String INVOICE_TRANSACTION_SUMMARIES_LOCATION_PREFIX = "/finance-storage/invoice-transaction-summaries/";

  private static final Logger log = LoggerFactory.getLogger(TransactionSummaryAPI.class);

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
   *                           order.{@link OrderTransactionSummary#getNumTransactions()} must be greater that 0
   *
   * @param okapiHeaders       A Map which represents okapi headers
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
      handleValidationError(summary.getNumTransactions(), asyncResultHandler);
    } else {
      String sql = "INSERT INTO " + getFullTableName(tenantId, ORDER_TRANSACTION_SUMMARIES)
          + " (id, jsonb) VALUES ($1, $2) ON CONFLICT (id) DO NOTHING";
      try {

        pgClient.execute(sql, Tuple.of(UUID.fromString(summary.getId()), pojo2JsonObject(summary)), result -> {
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

  private void handleValidationError(int numOfTransactions, Handler<AsyncResult<Response>> asyncResultHandler) {
    Parameter parameter = new Parameter().withKey("numOfTransactions")
      .withValue(String.valueOf(numOfTransactions));
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
  public void putFinanceStorageOrderTransactionSummariesById(String id, String lang, OrderTransactionSummary entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(ORDER_TRANSACTION_SUMMARIES, entity, id, okapiHeaders, vertxContext,
        PutFinanceStorageOrderTransactionSummariesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageOrderTransactionSummariesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(ORDER_TRANSACTION_SUMMARIES, id, okapiHeaders, vertxContext,
        DeleteFinanceStorageOrderTransactionSummariesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageInvoiceTransactionSummaries(InvoiceTransactionSummary summary, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (summary.getNumPaymentsCredits() < 1) {
      handleValidationError(summary.getNumPaymentsCredits(), asyncResultHandler);
    } else {
      String sql = "INSERT INTO " + getFullTableName(tenantId, INVOICE_TRANSACTION_SUMMARIES)
          + " (id, jsonb) VALUES ($1, $2) ON CONFLICT (id) DO NOTHING";
      try {
        pgClient.execute(sql, Tuple.of(UUID.fromString(summary.getId()), pojo2JsonObject(summary)), result -> {
          if (result.failed()) {
            String badRequestMessage = PgExceptionUtil.badRequestMessage(result.cause());
            if (badRequestMessage != null) {
              asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageInvoiceTransactionSummariesResponse
                .respond400WithTextPlain(Response.Status.BAD_REQUEST.getReasonPhrase())));
            } else {
              asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageInvoiceTransactionSummariesResponse
                .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
            }
          } else {
            log.debug("Preparing response to client");
            asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageInvoiceTransactionSummariesResponse
              .respond201WithApplicationJson(summary, PostFinanceStorageInvoiceTransactionSummariesResponse.headersFor201()
                .withLocation(INVOICE_TRANSACTION_SUMMARIES_LOCATION_PREFIX + summary.getId()))));
          }
        });
      } catch (Exception e) {
        asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageInvoiceTransactionSummariesResponse
          .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
      }
    }
  }

  @Override
  @Validate
  public void getFinanceStorageInvoiceTransactionSummariesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(INVOICE_TRANSACTION_SUMMARIES, InvoiceTransactionSummary.class, id, okapiHeaders, vertxContext,
        GetFinanceStorageInvoiceTransactionSummariesByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageInvoiceTransactionSummariesById(String id, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(INVOICE_TRANSACTION_SUMMARIES, id, okapiHeaders, vertxContext,
        DeleteFinanceStorageInvoiceTransactionSummariesByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putFinanceStorageInvoiceTransactionSummariesById(String id,String lang, InvoiceTransactionSummary entity, Map<String, String> okapiHeaders,
       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(INVOICE_TRANSACTION_SUMMARIES, entity, id, okapiHeaders, vertxContext,
      PutFinanceStorageInvoiceTransactionSummariesByIdResponse.class, asyncResultHandler);
  }

}
