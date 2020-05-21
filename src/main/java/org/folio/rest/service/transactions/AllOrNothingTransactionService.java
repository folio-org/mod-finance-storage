package org.folio.rest.service.transactions;

import static java.util.stream.Collectors.toList;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FinanceStorageAPI.LEDGERFY_TABLE;
import static org.folio.rest.impl.LedgerAPI.LEDGER_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getQueryValues;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.dao.BudgetDAO;
import org.folio.rest.dao.transactions.TemporaryTransactionDAO;
import org.folio.rest.dao.transactions.TransactionsDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Entity;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.service.BudgetService;
import org.folio.rest.service.summary.TransactionSummaryService;
import org.javamoney.moneta.Money;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public abstract class AllOrNothingTransactionService<T extends Entity> extends AbstractTransactionService {

  public static final String LEDGER_NOT_FOUND_FOR_TRANSACTION = "Ledger not found for transaction";
  public static final String TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION = "Transaction summary not found for transaction";
  public static final String ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED = "All expected transactions already processed";
  public static final String BUDGET_IS_INACTIVE = "Cannot create encumbrance from the not active budget";
  public static final String FUND_CANNOT_BE_PAID = "Fund cannot be paid due to restrictions";

  protected TransactionSummaryService<T> transactionSummaryService;
  protected BudgetService budgetService;
  protected TemporaryTransactionDAO temporaryTransactionDAO;
  protected TransactionsDAO transactionsDAO;
  protected BudgetDAO budgetDAO;


  AllOrNothingTransactionService(TemporaryTransactionDAO temporaryTransactionDAO,
                                 TransactionSummaryService<T> transactionSummaryService,
                                 TransactionsDAO transactionsDAO) {
    this.temporaryTransactionDAO = temporaryTransactionDAO;
    this.transactionSummaryService = transactionSummaryService;
    this.budgetService = new BudgetService();
    this.transactionsDAO = transactionsDAO;
    this.budgetDAO = new BudgetDAO();
  }

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, Context context, Map<String, String> headers) {
    try {
      handleValidationError(transaction);
    } catch (HttpStatusException e) {
      return  Future.failedFuture(e);
    }

    return verifyBudgetHasEnoughMoney(transaction, context, headers)
      .compose(v -> transactionSummaryService.getAndCheckTransactionSummary(transaction, context, headers)
        .compose(summary -> collectTempTransactions(transaction, context, headers)
          .compose(transactions -> {
            if (transactions.size() == transactionSummaryService.getNumTransactions(summary)) {
              return getTransactionFuture(context, headers, summary, transactions)
                .map(transaction);
            } else {
              return Future.succeededFuture(transaction);
            }
          })
        )
      );
  }

  private Future<Void> getTransactionFuture(Context context, Map<String, String> headers, T summary, List<Transaction> transactions) {
    DBClient client = new DBClient(context, headers);
    return client.startTx()
    .compose(t -> processTemporaryToPermanentTransactions(transactions, client))
    .compose(listTx -> moveFromTempToPermanentTable(summary, client))
    .compose(t -> client.endTx())
    .onComplete(result -> {
      if (result.failed()) {
        log.error("Transactions or associated data failed to be processed", result.cause());
       client.rollbackTransaction();
      } else {
        log.info("Transactions and associated data were successfully processed");
      }
    });
  }

  abstract Future<Void> processTemporaryToPermanentTransactions(List<Transaction> transactions, DBClient client);

  abstract String getSummaryId(Transaction transaction);

  abstract Void handleValidationError(Transaction transaction);

  protected abstract String getSelectBudgetsQuery(String tenantId);

  protected String getSelectBudgetsQuery(String sql, String tenantId, String tempTransactionTable) {
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
  Future<List<Transaction>> collectTempTransactions(Transaction transaction, Context context, Map<String, String> headers) {
    try {
      DBClient client = new DBClient(context, headers);
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

  public Future<Void> verifyBudgetHasEnoughMoney(Transaction transaction, Context context, Map<String, String> headers) {
    String fundId = transaction.getTransactionType() == Transaction.TransactionType.CREDIT ? transaction.getToFundId() : transaction.getFromFundId();

    return budgetService.getBudgetByFundIdAndFiscalYearId(transaction.getFiscalYearId(), fundId, context, headers)
      .compose(budget -> checkTransactionRestrictions(transaction, budget, context, headers));
  }


  private Future<Void> checkTransactionRestrictions(Transaction transaction, Budget budget, Context context, Map<String, String> headers) {
    Promise<Void> promise = Promise.promise();

    if (budget.getBudgetStatus() != Budget.BudgetStatus.ACTIVE) {
      log.error(BUDGET_IS_INACTIVE, budget.getId());
      promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), BUDGET_IS_INACTIVE));
    } else {
      return checkLedgerRestrictions(transaction, budget, context, headers);
    }
    return promise.future();
  }

  private Future<Void> checkLedgerRestrictions(Transaction transaction, Budget budget, Context context, Map<String, String> headers) {
    Promise<Void> promise = Promise.promise();
    getExistentLedger(transaction, context,  headers).onComplete(result -> {
      if (result.succeeded()) {
        if (isEncumbranceRestricted(transaction, budget, result.result())) {
          Money remainingAmountForEncumbrance = getBudgetRemainingAmountForEncumbrance(budget, transaction.getCurrency());
          if (Money.of(transaction.getAmount(), transaction.getCurrency()).isGreaterThan(remainingAmountForEncumbrance)) {
            promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), FUND_CANNOT_BE_PAID));
            return;
          }
        }
        else if (isPaymentRestricted(transaction, budget, result.result())) {
          Money remainingAmountForPayment = getBudgetRemainingAmountForPayment(budget, transaction.getCurrency());
          if (Money.of(transaction.getAmount(), transaction.getCurrency()).isGreaterThan(remainingAmountForPayment)) {
            promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), FUND_CANNOT_BE_PAID));
            return;
          }
        }
        promise.complete();
      } else {
        handleFailure(promise, result);
      }
    });
    return promise.future();
  }


  private boolean isEncumbranceRestricted(Transaction transaction, Budget budget, Ledger ledger) {
    return transaction.getTransactionType() == Transaction.TransactionType.ENCUMBRANCE && ledger.getRestrictEncumbrance()
      && budget.getAllowableEncumbrance() != null;
  }

  /**
   * Calculates remaining amount for encumbrance
   * [remaining amount] = (allocated * allowableEncumbered) - (allocated - (unavailable + available)) - (encumbered + awaitingPayment + expenditures)
   *
   * @param budget     processed budget
   * @param currency   processed transaction currency
   * @return remaining amount for encumbrance
   */
  private Money getBudgetRemainingAmountForEncumbrance(Budget budget, String currency) {
    Money allocated = Money.of(budget.getAllocated(), currency);
    // get allowableEncumbered converted from percentage value
    double allowableEncumbered = Money.of(budget.getAllowableEncumbrance(), currency).divide(100d).getNumber().doubleValue();
    Money unavailable = Money.of(budget.getUnavailable(), currency);
    Money available = Money.of(budget.getAvailable(), currency);
    Money encumbered = Money.of(budget.getEncumbered(), currency);
    Money awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);
    Money expenditures = Money.of(budget.getExpenditures(), currency);

    Money result = allocated.multiply(allowableEncumbered);
    result = result.subtract(allocated.subtract(unavailable.add(available)));
    result = result.subtract(encumbered.add(awaitingPayment).add(expenditures));

    return result;
  }

  /**
   * Calculates remaining amount for payment
   * [remaining amount] = (allocated * allowableExpenditure) - (allocated - (unavailable + available)) - (awaitingPayment + expended)
   *
   * @param budget     processed budget
   * @param currency   processed transaction currency
   * @return remaining amount for payment
   */
  private Money getBudgetRemainingAmountForPayment(Budget budget, String currency) {
    Money allocated = Money.of(budget.getAllocated(), currency);
    // get allowableExpenditure from percentage value
    double allowableExpenditure = Money.of(budget.getAllowableExpenditure(), currency).divide(100d).getNumber().doubleValue();
    Money unavailable = Money.of(budget.getUnavailable(), currency);
    Money available = Money.of(budget.getAvailable(), currency);
    Money expenditure = Money.of(budget.getExpenditures(), currency);
    Money awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);

    Money result = allocated.multiply(allowableExpenditure);
    result = result.subtract(allocated.subtract(unavailable.add(available)));
    result = result.subtract(expenditure.add(awaitingPayment));

    return result;
  }

  private boolean isPaymentRestricted(Transaction transaction, Budget budget, Ledger ledger) {
    return transaction.getTransactionType() == Transaction.TransactionType.PAYMENT && ledger.getRestrictExpenditures()
      && budget.getAllowableExpenditure() != null;
  }

  private Future<Ledger> getExistentLedger(Transaction transaction, Context context, Map<String, String> headers) {
    Promise<Ledger> promise = Promise.promise();
    String fundId = transaction.getTransactionType() == Transaction.TransactionType.CREDIT ? transaction.getToFundId() : transaction.getFromFundId();
    String query = "fund.id =" + fundId;
    PgUtil.get(LEDGER_TABLE, Ledger.class, LedgerCollection.class, query , 0, 1, headers, context,
      FinanceStorageLedgers.GetFinanceStorageLedgersResponse.class, reply-> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else if (((LedgerCollection) reply.result().getEntity()).getLedgers().isEmpty()) {
          log.error(LEDGER_NOT_FOUND_FOR_TRANSACTION);
          promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), LEDGER_NOT_FOUND_FOR_TRANSACTION));
        } else {
          promise.complete(((LedgerCollection) reply.result().getEntity()).getLedgers().get(0));
        }
      });
    return promise.future();
  }

  protected Future<Void> moveFromTempToPermanentTable(T summary, DBClient client) {
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

  Future<Void> updateLedgerFYs(List<LedgerFY> ledgerFYs, DBClient client) {
    Promise<Void> promise = Promise.promise();
    if (ledgerFYs.isEmpty()) {
      promise.complete();
    } else {
      String sql = getLedgerFyUpdateQuery(ledgerFYs, client.getTenantId());
      client.getPgClient().execute(client.getConnection(), sql, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete();
        }
      });
    }
    return promise.future();
  }

  private String getLedgerFyUpdateQuery(List<LedgerFY> ledgerFYs, String tenantId) {
    List<JsonObject> jsonLedgerFYs = ledgerFYs.stream().map(JsonObject::mapFrom).collect(toList());
    return String.format("UPDATE %s AS ledger_fy SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) "
      + "WHERE b.id::uuid = ledger_fy.id;", getFullTableName(tenantId, LEDGERFY_TABLE), HelperUtils.getQueryValues(jsonLedgerFYs));
  }

  protected String buildUpdateBudgetsQuery(List<JsonObject> jsonBudgets, String tenantId) {
    return String.format(
      "UPDATE %s AS budgets SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) WHERE b.id::uuid = budgets.id;",
      getFullTableName(tenantId, BUDGET_TABLE), getQueryValues(jsonBudgets));
  }

  public List<Error> buildNullValidationError(String value, String key) {
    if (value == null) {
      Parameter parameter = new Parameter().withKey(key)
        .withValue("null");
      Error error = new Error().withCode("-1")
        .withMessage("may not be null")
        .withParameters(Collections.singletonList(parameter));
      return Collections.singletonList(error);
    }
    return Collections.emptyList();
  }

  private void releaseLock(Lock lock, String lockName) {
    log.info("Released lock {}", lockName);
    lock.release();
  }

}
