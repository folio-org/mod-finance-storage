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
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.service.summary.TransactionSummaryService;
import org.folio.service.transactions.restriction.TransactionRestrictionService;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
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
      .compose(v -> transactionRestrictionService.verifyBudgetHasEnoughMoney(transaction, client))
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
    return Future.succeededFuture()
      .map(v -> {
        transactionRestrictionService.handleValidationError(transaction);
        return null;
      });
  }

  private Future<Void> cleanupTempTransactions(Transaction transaction, DBClient client) {
    final String summaryId = transactionSummaryService.getSummaryId(transaction);
    return temporaryTransactionDAO.deleteTempTransactionsWithNewConn(summaryId, client)
      .recover(t -> {
        log.error("Can't delete temporary transaction for {}", summaryId, t);
        return Future.failedFuture(t);
      })
      .mapEmpty();
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
  private Future<Void> processAllOrNothing(Transaction transaction, DBClient client, BiFunction<List<Transaction>, DBClient, Future<Void>> operation) {
    return transactionSummaryService.getAndCheckTransactionSummary(transaction, client)
      .compose(summary -> addTempTransactionSequentially(transaction, client)
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

  /**
   * This method uses SELECT FOR UPDATE locking on summary table by summaryId.
   * So in this case requests to create temp transaction and get temp transaction count for the same summaryId
   * will be executed only by a single thread.
   * The other thread will wait until DB Lock is released when the database transaction ends.
   * Method {@link org.folio.rest.persist.PostgresClient#withTrans(Function)} ends the database transaction after executing.
   *
   * @param transaction temp transaction to create
   * @param client the db client
   * @return future with list of temp transactions
   */
  private Future<List<Transaction>> addTempTransactionSequentially(Transaction transaction, DBClient client) {
    final String tenantId = client.getTenantId();
    final String summaryId = transactionSummaryService.getSummaryId(transaction);
    return client.getPgClient().withTrans(conn -> transactionSummaryService.getTransactionSummaryWithLocking(summaryId, conn)
      .compose(ar -> temporaryTransactionDAO.createTempTransaction(transaction, summaryId, tenantId, conn))
      .compose(tr -> temporaryTransactionDAO.getTempTransactionsBySummaryId(summaryId, conn)));
  }

}
