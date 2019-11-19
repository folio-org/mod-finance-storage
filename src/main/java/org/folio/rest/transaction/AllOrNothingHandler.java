package org.folio.rest.transaction;

import static org.folio.rest.persist.HelperUtils.handleFailure;
import static org.folio.rest.persist.HelperUtils.rollbackTransaction;
import static org.folio.rest.persist.HelperUtils.startTx;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public abstract class AllOrNothingHandler extends AbstractTransactionHandler {

  private String temporaryTransactionTable;
  private String summaryTable;
  private boolean isLastRecord;

  AllOrNothingHandler(String temporaryTransactionTable, String summaryTable, Map<String, String> okapiHeaders, Context context,
      Handler<AsyncResult<Response>> handler) {
    super(okapiHeaders, context, handler);
    this.temporaryTransactionTable = temporaryTransactionTable;
    this.summaryTable = summaryTable;
    this.isLastRecord = false;
  }

  public void createTransaction(Transaction transaction) {
    processAllOrNothing(transaction, this::createPermanentTransactions).setHandler(result -> {
      if (result.failed()) {
        HttpStatusException cause = (HttpStatusException) result.cause();
        if (cause.getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
          getAsyncResultHandler().handle(Future.succeededFuture(
              FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.respond400WithTextPlain(cause.getPayload())));
        } else {
          getAsyncResultHandler().handle(Future.succeededFuture(FinanceStorageTransactions.PostFinanceStorageTransactionsResponse
            .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
        }
      } else {
        log.debug("Preparing response to client");
        getAsyncResultHandler().handle(
            Future.succeededFuture(FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.respond201WithApplicationJson(
                transaction, FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.headersFor201()
                  .withLocation(TRANSACTION_LOCATION_PREFIX + transaction.getId()))));
      }
    });
  }

  abstract String getSummaryId(Transaction transaction);

  abstract Criterion getTransactionBySummaryIdCriterion(String value);

  private Future<Tx<List<Transaction>>> createPermanentTransactions(Tx<List<Transaction>> tx) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    List<Transaction> transactions = tx.getEntity();
    tx.getPgClient()
      .saveBatch(tx.getConnection(), TRANSACTION_TABLE, transactions, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(tx);
        }
      });
    return promise.future();
  }

  /**
   * Accumulate transactions in a temporary table until expected number of transactions are present, then apply them all at once,
   * updating all the required tables together in a database transaction.
   * 
   * @param transaction         processed transaction
   * @param processTransactions The function responsible for the logic of saving transactions from the temporary table to the
   *                            permanent one.
   * @return completed future
   */
  Future<Void> processAllOrNothing(Transaction transaction,
      Function<Tx<List<Transaction>>, Future<Tx<List<Transaction>>>> processTransactions) {
    return createTempTransaction(transaction).compose(this::getSummary)
      .compose(this::getTempTransactions)
      .compose(transactions -> {
        Promise<Void> promise = Promise.promise();
        try {
          if (isLastRecord) {
            promise = moveFromTempToPermanentTable(processTransactions, transactions);
          } else {
            promise.complete();
          }
        } catch (Exception e) {
          promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
        }
        return promise.future();
      });
  }

  private Promise<Void> moveFromTempToPermanentTable(
      Function<Tx<List<Transaction>>, Future<Tx<List<Transaction>>>> processTransactions, List<Transaction> transactions) {

    Promise<Void> promise = Promise.promise();
    Tx<List<Transaction>> tx = new Tx<>(transactions, getPostgresClient());

    startTx(tx).compose(processTransactions)
      .compose(this::deleteTempTransactions)
      .compose(HelperUtils::endTx)
      .setHandler(result -> {
        if (result.failed()) {
          HttpStatusException cause = (HttpStatusException) result.cause();
          log.error("Transactions {} or associated data failed to be processed", cause, tx.getEntity());

          rollbackTransaction(tx).setHandler(res -> promise.fail(cause));
        } else {
          log.info("Transactions {} and associated data were successfully processed", tx.getEntity());
          promise.complete();
        }
      });

    return promise;
  }

  private Future<Transaction> createTempTransaction(Transaction transaction) {
    Promise<Transaction> promise = Promise.promise();

    if (transaction.getId() == null) {
      transaction.setId(UUID.randomUUID()
        .toString());
    }

    log.debug("Creating new transaction with id={}", transaction.getId());

    getPostgresClient().save(temporaryTransactionTable, transaction.getId(), transaction, reply -> {
      if (reply.failed()) {
        log.error("Transaction creation with id={} failed", reply.cause(), transaction.getId());
        handleFailure(promise, reply);
      } else {
        log.debug("New transaction with id={} successfully created", transaction.getId());
        promise.complete(transaction);
      }
    });
    return promise.future();
  }

  private Future<JsonObject> getSummary(Transaction transaction) {
    Promise<JsonObject> promise = Promise.promise();

    log.debug("Get summary={}", getSummaryId(transaction));

    getPostgresClient().getById(summaryTable, getSummaryId(transaction), reply -> {
      if (reply.failed()) {
        log.error("Summary retrieval with id={} failed", reply.cause(), transaction.getId());
        handleFailure(promise, reply);
      } else {
        log.debug("Summary with id={} successfully extracted", transaction.getId());
        promise.complete(reply.result());
      }
    });
    return promise.future();
  }

  private Future<List<Transaction>> getTempTransactions(JsonObject summary) {
    Promise<List<Transaction>> promise = Promise.promise();

    Criterion criterion = getTransactionBySummaryIdCriterion(summary.getString("id"));

    getPostgresClient().get(temporaryTransactionTable, Transaction.class, criterion, true, false, reply -> {
      if (reply.failed()) {
        log.error("Failed to extract temporary transaction by summary id={}", reply.cause(), summary.getString("id"));
        handleFailure(promise, reply);
      } else {
        List<Transaction> transactions = reply.result()
          .getResults();
        isLastRecord = transactions.size() == summary.getInteger("numTransactions");
        promise.complete(transactions);
      }
    });
    return promise.future();
  }

  private Future<Tx<List<Transaction>>> deleteTempTransactions(Tx<List<Transaction>> tx) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    Transaction transaction = tx.getEntity()
      .get(0);

    Criterion criterion = getTransactionBySummaryIdCriterion(getSummaryId(transaction));

    tx.getPgClient()
      .delete(tx.getConnection(), temporaryTransactionTable, criterion, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(tx);
        }
      });
    return promise.future();
  }

  String getTemporaryTransactionTable() {
    return temporaryTransactionTable;
  }

  String getFullTransactionTableName() {
    return HelperUtils.getFullTableName(getTenantId(), TRANSACTION_TABLE);
  }

  String getFullTemporaryTransactionTableName() {
    return HelperUtils.getFullTableName(getTenantId(), temporaryTransactionTable);
  }

}
