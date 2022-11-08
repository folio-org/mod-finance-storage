package org.folio.service.transactions;

import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;

import java.util.List;
import java.util.function.BiFunction;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.service.summary.TransactionSummaryService;
import org.folio.service.transactions.restriction.TransactionRestrictionService;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.handler.HttpException;

public class AllOrNothingTransactionService {

  private static final Logger log = LogManager.getLogger(AllOrNothingTransactionService.class);

  public static final String TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION = "Transaction summary not found for transaction";
  public static final String ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED = "All expected transactions already processed";
  public static final String BUDGET_IS_INACTIVE = "Cannot create encumbrance from the not active budget {}";

  private final TransactionDAO transactionDAO;
  private final TemporaryTransactionDAO temporaryTransactionDAO;
  private final TransactionSummaryService transactionSummaryService;
  private final TransactionRestrictionService transactionRestrictionService;
  private final DBClientFactory dbClientFactory;

  public AllOrNothingTransactionService(TransactionDAO transactionDAO,
                                        TemporaryTransactionDAO temporaryTransactionDAO,
                                        TransactionSummaryService transactionSummaryService,
                                        TransactionRestrictionService transactionRestrictionService,
                                        DBClientFactory dbClientFactory) {
    this.transactionDAO = transactionDAO;
    this.temporaryTransactionDAO = temporaryTransactionDAO;
    this.transactionSummaryService = transactionSummaryService;
    this.transactionRestrictionService = transactionRestrictionService;
    this.dbClientFactory = dbClientFactory;
  }

  public Future<Transaction> createTransaction(Transaction transaction, RequestContext requestContext,
          BiFunction<List<Transaction>, DBClient, Future<Void>> operation) {
    DBClient client = dbClientFactory.getDbClient(requestContext);
    return validateTransactionAsFuture(transaction)
//      .compose(v -> transactionRestrictionService.verifyBudgetHasEnoughMoney(transaction, client))
      .compose(v -> processAllOrNothing(transaction, client, operation))
      .map(transaction)
      .recover(throwable -> cleanupTempTransactions(transaction, client)
        .compose(v -> Future.failedFuture(throwable), v -> Future.failedFuture(throwable)));
  }

  public Future<Void> updateTransaction(Transaction transaction, RequestContext requestContext,
          BiFunction<List<Transaction>, DBClient, Future<Void>> operation) {
    DBClient client = dbClientFactory.getDbClient(requestContext);
    return validateTransactionAsFuture(transaction)
      .compose(v -> verifyTransactionExistence(transaction.getId(), client))
      .compose(v -> processAllOrNothing(transaction, client, operation))
      .recover(throwable -> cleanupTempTransactions(transaction, client)
        .compose(v -> Future.failedFuture(throwable), v -> Future.failedFuture(throwable)));
  }

  private Future<Void> validateTransactionAsFuture(Transaction transaction) {
    try {
      transactionRestrictionService.handleValidationError(transaction);
      return Future.succeededFuture();
    } catch (HttpException e) {
      return Future.failedFuture(e);
    }
  }

  private Future<Void> cleanupTempTransactions(Transaction transaction, DBClient client) {
    final String summaryId = transactionSummaryService.getSummaryId(transaction);
    return temporaryTransactionDAO.deleteTempTransactionsWithNewConn(summaryId, client)
                                  .compose(count -> Future.succeededFuture(null), t -> {
                                    log.error("Can't delete temporary transaction for {}", summaryId);
                                    return Future.failedFuture(t);
                                  });
  }

  private Future<Void> processAllOrNothing(Transaction transaction, DBClient client, BiFunction<List<Transaction>, DBClient, Future<Void>> operation) {
    return transactionSummaryService.getAndCheckTransactionSummary(transaction, client)
      .compose(summary -> collectTempTransactions(transaction, client)
        .compose(transactions -> {
          if (transactions.size() == transactionSummaryService.getNumTransactions(summary)) {
            return client.startTx()
              // handle create or update
              .compose(dbClient -> operation.apply(transactions, dbClient))
              .compose(ok -> finishAllOrNothing(summary, client))
              .compose(ok -> client.endTx())
              .onComplete(result -> {
                if (result.failed()) {
                  log.error("Transactions or associated data failed to be processed", result.cause());
                  client.rollbackTransaction();
                } else {
                  log.info("Transactions and associated data were successfully processed");
                }
              });

          } else {
            return Future.succeededFuture();
          }
        })
      );
  }

  /**
   * Accumulate transactions in a temporary table until expected number of transactions are present, then apply them all at once,
   * updating all the required tables together in a database transaction.
   *
   * @param transaction         processed transaction
   *                            permanent one.
   * @return completed future
   */
  Future<List<Transaction>> collectTempTransactions(Transaction transaction, DBClient client) {
    try {
      return addTempTransactionSequentially(transaction, client)
        .compose(transactions -> {
          Promise<List<Transaction>> promise = Promise.promise();
          try {
            promise.complete(transactions);
          } catch (Exception e) {
            promise.fail(new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
          }
          return promise.future();
        });
    } catch (HttpException e) {
      return Future.failedFuture(e);
    }
  }

  private Future<Void> verifyTransactionExistence(String transactionId, DBClient client) {
    CriterionBuilder criterionBuilder = new CriterionBuilder();
    criterionBuilder.with("id", transactionId);
    return transactionDAO.getTransactions(criterionBuilder.build(), client)
      .map(transactions -> {
        if (transactions.isEmpty()) {
          throw new HttpException(Response.Status.NOT_FOUND.getStatusCode(), "Transaction not found");
        }
        return null;
      });
  }

  private Future<Void> finishAllOrNothing(JsonObject summary, DBClient client) {
    return temporaryTransactionDAO.deleteTempTransactions(summary.getString(ID_FIELD_NAME), client)
      .compose(tr -> transactionSummaryService.setTransactionsSummariesProcessed(summary, client)) ;
  }

  private Future<List<Transaction>> addTempTransactionSequentially(Transaction transaction, DBClient client) {
    Promise<List<Transaction>> promise = Promise.promise();
    SharedData sharedData = client.getVertx().sharedData();
    // define unique lockName based on combination of transactions type and summary id
    final String summaryId = transactionSummaryService.getSummaryId(transaction);
    String lockName = transaction.getTransactionType() + summaryId;

    sharedData.getLock(lockName, lockResult -> {
      if (lockResult.succeeded()) {
        log.info("Got lock {}", lockName);
        Lock lock = lockResult.result();
        try {
          client.getVertx().setTimer(30000, timerId -> releaseLock(lock, lockName));

          temporaryTransactionDAO.createTempTransaction(transaction, summaryId, client)
            .compose(tr -> temporaryTransactionDAO.getTempTransactionsBySummaryId(summaryId, client))
            .onComplete(trnsResult -> {
              releaseLock(lock, lockName);
              if (trnsResult.succeeded()) {
                promise.complete(trnsResult.result());
              } else {
                promise.fail(trnsResult.cause());
              }
            });
        } catch (Exception e) {
          releaseLock(lock, lockName);
          promise.fail(e);
        }
      } else {
        promise.fail(new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), lockResult.cause().getMessage()));
      }
    });

    return promise.future();
  }

  private void releaseLock(Lock lock, String lockName) {
    log.info("Released lock {}", lockName);
    lock.release();
  }

}
