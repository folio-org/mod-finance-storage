package org.folio.service.transactions;

import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;
import org.folio.service.summary.TransactionSummaryService;
import org.folio.service.transactions.restriction.TransactionRestrictionService;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;

public class AllOrNothingTransactionService {

  private static final Logger logger = LogManager.getLogger(AllOrNothingTransactionService.class);

  public static final String TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION = "Transaction summary not found for transaction";
  public static final String ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED = "All expected transactions already processed";

  private final TransactionDAO transactionDAO;
  private final TemporaryTransactionDAO temporaryTransactionDAO;
  private final TransactionSummaryService transactionSummaryService;
  private final TransactionRestrictionService transactionRestrictionService;

  public AllOrNothingTransactionService(TransactionDAO transactionDAO,
                                        TemporaryTransactionDAO temporaryTransactionDAO,
                                        TransactionSummaryService transactionSummaryService,
                                        TransactionRestrictionService transactionRestrictionService) {
    this.transactionDAO = transactionDAO;
    this.temporaryTransactionDAO = temporaryTransactionDAO;
    this.transactionSummaryService = transactionSummaryService;
    this.transactionRestrictionService = transactionRestrictionService;
  }

  public Future<Transaction> createTransaction(Transaction transaction, DBConn conn,
      BiFunction<List<Transaction>, DBConn, Future<Void>> operation) {
    return validateTransactionAsFuture(transaction)
      .compose(v -> transactionRestrictionService.verifyBudgetHasEnoughMoney(transaction, conn))
      .compose(v -> processAllOrNothing(transaction, conn, operation))
      .map(transaction);
  }

  public Future<Void> updateTransaction(Transaction transaction, DBConn conn,
      BiFunction<List<Transaction>, DBConn, Future<Void>> operation) {
    return validateTransactionAsFuture(transaction)
      .compose(v -> verifyTransactionExistence(transaction.getId(), conn))
      .compose(v -> processAllOrNothing(transaction, conn, operation));
  }

  private Future<Void> validateTransactionAsFuture(Transaction transaction) {
    return Future.succeededFuture()
      .map(v -> {
        transactionRestrictionService.handleValidationError(transaction);
        return null;
      });
  }

  /**
   * Accumulate transactions in a temporary table until expected number of transactions are present,
   * then apply them all at once.
   * Updating all the required tables together is occurred in a database transaction.
   *
   * @param transaction processed transaction
   *
   * @return future with void
   */
  private Future<Void> processAllOrNothing(Transaction transaction, DBConn conn, BiFunction<List<Transaction>, DBConn, Future<Void>> operation) {
    return transactionSummaryService.getAndCheckTransactionSummary(transaction, conn)
      .recover(t -> {
        if (t instanceof HttpException he && he.getStatusCode() == 404) {
          logger.error("Summary was not found when processing transaction with id {}", transaction.getId());
          return Future.failedFuture(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(),
            "Cannot process the transaction because the summary was not found.", t));
        }
        return Future.failedFuture(t);
      })
      .compose(summary -> addTempTransactionSequentially(transaction, conn)
        .compose(transactions -> {
          if (transactions.size() != transactionSummaryService.getNumTransactions(summary)) {
            return Future.succeededFuture();
          }
          // handle create or update
          return operation.apply(transactions, conn)
            .compose(ok -> finishAllOrNothing(summary, conn))
            .onComplete(result -> {
              if (result.failed()) {
                logger.error("processAllOrNothing:: Transactions with id {} or associated data failed to be processed",
                  transaction.getId(), result.cause());
              } else {
                logger.info("processAllOrNothing:: Transactions with id {} and associated data were successfully processed", transaction.getId());
              }
            });
        })
      );
  }

  private Future<Void> verifyTransactionExistence(String transactionId, DBConn conn) {
    CriterionBuilder criterionBuilder = new CriterionBuilder();
    criterionBuilder.with("id", transactionId);
    return transactionDAO.getTransactions(criterionBuilder.build(), conn)
      .map(transactions -> {
        if (transactions.isEmpty()) {
          logger.warn("verifyTransactionExistence:: Transaction with id {} not found", transactionId);
          throw new HttpException(Response.Status.NOT_FOUND.getStatusCode(), "Transaction not found");
        }
        return null;
      });
  }

  private Future<Void> finishAllOrNothing(JsonObject summary, DBConn conn) {
    return temporaryTransactionDAO.deleteTempTransactions(summary.getString(ID_FIELD_NAME), conn)
      .compose(tr -> transactionSummaryService.setTransactionsSummariesProcessed(summary, conn)) ;
  }

  /**
   * This method uses SELECT FOR UPDATE locking on summary table by summaryId.
   * So in this case requests to create temp transaction and get temp transaction count for the same summaryId
   * will be executed only by a single thread.
   * The other thread will wait until DB Lock is released when the database transaction ends.
   * Method {@link org.folio.rest.persist.PostgresClient#withTrans(Function)} ends the database transaction after executing.
   *
   * @param transaction temp transaction to create
   * @param conn the db connection
   * @return future with list of temp transactions
   */
  private Future<List<Transaction>> addTempTransactionSequentially(Transaction transaction, DBConn conn) {
    final String tenantId = conn.getTenantId();
    final String summaryId = transactionSummaryService.getSummaryId(transaction);
    return transactionSummaryService.getTransactionSummaryWithLocking(summaryId, conn)
      .compose(ar -> temporaryTransactionDAO.createTempTransaction(transaction, summaryId, tenantId, conn))
      .compose(tr -> temporaryTransactionDAO.getTempTransactionsBySummaryId(summaryId, conn));
  }

}
