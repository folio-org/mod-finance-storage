package org.folio.service.transactions;

import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Entity;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.folio.service.calculation.CalculationService;
import org.folio.service.fund.FundService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledgerfy.LedgerFiscalYearService;
import org.folio.service.summary.TransactionSummaryService;
import org.javamoney.moneta.Money;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public abstract class BaseAllOrNothingTransactionService<T extends Entity> extends AbstractTransactionService {

  public static final String TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION = "Transaction summary not found for transaction";
  public static final String ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED = "All expected transactions already processed";
  public static final String BUDGET_IS_INACTIVE = "Cannot create encumbrance from the not active budget";
  public static final String FUND_CANNOT_BE_PAID = "Fund cannot be paid due to restrictions";

  BudgetService budgetService;
  CalculationService calculationService;
  private final TemporaryTransactionDAO temporaryTransactionDAO;
  private final LedgerFiscalYearService ledgerFiscalYearService;
  private final FundService fundService;
  TransactionSummaryService<T> transactionSummaryService;
  TransactionDAO transactionsDAO;
  private final LedgerService ledgerService;


  public BaseAllOrNothingTransactionService(BudgetService budgetService,
                                            TemporaryTransactionDAO temporaryTransactionDAO,
                                            CalculationService calculationService,
                                            LedgerFiscalYearService ledgerFiscalYearService,
                                            FundService fundService,
                                            TransactionSummaryService<T> transactionSummaryService,
                                            TransactionDAO transactionsDAO,
                                            LedgerService ledgerService) {
    this.budgetService = budgetService;
    this.temporaryTransactionDAO = temporaryTransactionDAO;
    this.calculationService = calculationService;
    this.ledgerFiscalYearService = ledgerFiscalYearService;
    this.fundService = fundService;
    this.transactionSummaryService = transactionSummaryService;
    this.transactionsDAO = transactionsDAO;
    this.ledgerService = ledgerService;
  }

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, Context context, Map<String, String> headers) {
    try {
      handleValidationError(transaction);
    } catch (HttpStatusException e) {
      return  Future.failedFuture(e);
    }

    DBClient client = new DBClient(context, headers);
    return verifyBudgetHasEnoughMoney(transaction, client)
      .compose(v -> processTransactions(transaction, client))
      .map(transaction);
  }

  protected Future<Void> processTransactions(Transaction transaction, DBClient client) {
    return transactionSummaryService.getAndCheckTransactionSummary(transaction, client)
      .compose(summary -> collectTempTransactions(transaction, client)
        .compose(transactions -> {
          if (transactions.size() == transactionSummaryService.getNumTransactions(summary)) {
            return client.startTx()
              // handle create or update
              .compose(dbClient -> processTemporaryToPermanentTransactions(transactions, client))
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

  String getSelectBudgetsQuery(String sql, String tenantId, String tempTransactionTable) {
    return String.format(sql, getFullTableName(tenantId, BUDGET_TABLE), getFullTableName(tenantId, tempTransactionTable));
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
            promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
          }
          return promise.future();
        });
    } catch (HttpStatusException e) {
      return Future.failedFuture(e);
    }
  }

  Future<Void> verifyBudgetHasEnoughMoney(Transaction transaction, DBClient dbClient) {

    String fundId = transaction.getTransactionType() == Transaction.TransactionType.CREDIT ? transaction.getToFundId() : transaction.getFromFundId();

    return budgetService.getBudgetByFundIdAndFiscalYearId(transaction.getFiscalYearId(), fundId, dbClient)
      .compose(budget -> {
        if (budget.getBudgetStatus() != Budget.BudgetStatus.ACTIVE) {
          log.error(BUDGET_IS_INACTIVE, budget.getId());
          return Future.failedFuture(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), BUDGET_IS_INACTIVE));
        }
        if (transaction.getTransactionType() == Transaction.TransactionType.CREDIT || transaction.getAmount() <= 0) {
          return Future.succeededFuture();
        }
        return getRelatedTransaction(transaction, dbClient)
          .compose(relatedTransaction -> ledgerService.getLedgerByTransaction(transaction, dbClient)
            .map(ledger -> checkTransactionAllowed(transaction, relatedTransaction, budget, ledger)));
      });
  }

  protected Future<Transaction> getRelatedTransaction(Transaction transaction, DBClient dbClient) {
    return Future.succeededFuture(null);
  }

  private Void checkTransactionAllowed(Transaction transaction, Transaction relatedTransaction, Budget budget, Ledger ledger) {
    if (isTransactionOverspendRestricted(ledger, budget)) {
      Money budgetRemainingAmount = getBudgetRemainingAmount(budget, transaction.getCurrency(), relatedTransaction);
      if (Money.of(transaction.getAmount(), transaction.getCurrency()).isGreaterThan(budgetRemainingAmount)) {
        throw new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), FUND_CANNOT_BE_PAID);
      }
    }
    return null;
  }

  Future<Void> finishAllOrNothing(T summary, DBClient client) {
    return temporaryTransactionDAO.deleteTempTransactions(summary.getId(), client)
      .compose(tr -> transactionSummaryService.setTransactionsSummariesProcessed(summary, client)) ;
  }

  private Future<List<Transaction>> addTempTransactionSequentially(Transaction transaction, DBClient client) {
    Promise<List<Transaction>> promise = Promise.promise();
    SharedData sharedData = client.getVertx().sharedData();
    // define unique lockName based on combination of transactions type and summary id
    String lockName = transaction.getTransactionType() + getSummaryId(transaction);

    sharedData.getLock(lockName, lockResult -> {
      if (lockResult.succeeded()) {
        log.info("Got lock {}", lockName);
        Lock lock = lockResult.result();
        try {
          client.getVertx().setTimer(30000, timerId -> releaseLock(lock, lockName));

          temporaryTransactionDAO.createTempTransaction(transaction, getSummaryId(transaction), client)
            .compose(tr -> temporaryTransactionDAO.getTempTransactionsBySummaryId(getSummaryId(transaction), client))
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
        promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), lockResult.cause().getMessage()));
      }
    });

    return promise.future();
  }

  private void releaseLock(Lock lock, String lockName) {
    log.info("Released lock {}", lockName);
    lock.release();
  }


  abstract Money getBudgetRemainingAmount(Budget budget, String currency, Transaction relatedTransaction);

  abstract Future<Void> processTemporaryToPermanentTransactions(List<Transaction> transactions, DBClient client);

  abstract String getSummaryId(Transaction transaction);

  abstract Void handleValidationError(Transaction transaction);

  protected abstract String getSelectBudgetsQuery(String tenantId);

  protected abstract boolean isTransactionOverspendRestricted(Ledger ledger, Budget budget);

}
