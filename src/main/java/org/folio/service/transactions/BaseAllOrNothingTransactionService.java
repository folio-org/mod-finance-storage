package org.folio.service.transactions;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.ws.rs.core.Response;

import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Entity;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.folio.service.fund.FundService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledgerfy.LedgerFiscalYearService;
import org.folio.service.summary.TransactionSummaryService;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryFunctions;

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
  private final TemporaryTransactionDAO temporaryTransactionDAO;
  private final LedgerFiscalYearService ledgerFiscalYearService;
  private final FundService fundService;
  TransactionSummaryService<T> transactionSummaryService;
  TransactionDAO transactionsDAO;
  private final LedgerService ledgerService;


  public BaseAllOrNothingTransactionService(BudgetService budgetService,
                                            TemporaryTransactionDAO temporaryTransactionDAO,
                                            LedgerFiscalYearService ledgerFiscalYearService,
                                            FundService fundService,
                                            TransactionSummaryService<T> transactionSummaryService,
                                            TransactionDAO transactionsDAO,
                                            LedgerService ledgerService) {
    this.budgetService = budgetService;
    this.temporaryTransactionDAO = temporaryTransactionDAO;
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
    return verifyBudgetHasEnoughMoney(transaction, context, headers)
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

  Future<Void> verifyBudgetHasEnoughMoney(Transaction transaction, Context context, Map<String, String> headers) {

    String fundId = transaction.getTransactionType() == Transaction.TransactionType.CREDIT ? transaction.getToFundId() : transaction.getFromFundId();

    return budgetService.getBudgetByFundIdAndFiscalYearId(transaction.getFiscalYearId(), fundId, context, headers)
      .compose(budget -> {
        if (budget.getBudgetStatus() != Budget.BudgetStatus.ACTIVE) {
          log.error(BUDGET_IS_INACTIVE, budget.getId());
          return Future.failedFuture(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), BUDGET_IS_INACTIVE));
        }
        if (transaction.getTransactionType() == Transaction.TransactionType.CREDIT || transaction.getAmount() <= 0) {
          return Future.succeededFuture();
        }
        return ledgerService.getLedgerByTransaction(transaction, context,  headers)
          .map(ledger -> checkTransactionAllowed(transaction, budget, ledger));
      });
  }

  private Void checkTransactionAllowed(Transaction transaction, Budget budget, Ledger ledger) {
    if (isTransactionOverspendRestricted(ledger, budget)) {
      Money budgetRemainingAmount = getBudgetRemainingAmount(budget, transaction);
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

  List<Error> buildNullValidationError(String value, String key) {
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

  Future<Void> updateLedgerFYsWithTotals(List<Budget> oldBudgets, List<Budget> newBudgets, DBClient client) {
    return getGroupedFundIdsByLedgerFy(newBudgets, client)
      .map(fundIdsGroupedByLedgerFY -> calculateLedgerFyTotals(fundIdsGroupedByLedgerFY, oldBudgets, newBudgets))
      .compose(ledgersFYears -> ledgerFiscalYearService.updateLedgerFiscalYears(ledgersFYears, client));
  }

  private List<LedgerFY> calculateLedgerFyTotals(Map<LedgerFY, Set<String>> fundIdsGroupedByLedgerFY, List<Budget> oldBudgets, List<Budget> newBudgets) {
    String currency = fundIdsGroupedByLedgerFY.keySet().stream().limit(1).map(LedgerFY::getCurrency).findFirst().orElse("USD"); // there always must be at least one ledgerFY record
    Map<String, MonetaryAmount> oldAvailableByFundId = oldBudgets.stream().collect(groupingBy(Budget::getFundId, sumAvailable(currency)));
    Map<String, MonetaryAmount> oldUnavailableByFundId = oldBudgets.stream().collect(groupingBy(Budget::getFundId, sumUnavailable(currency)));

    Map<String, MonetaryAmount> newAvailableByFundId = newBudgets.stream().collect(groupingBy(Budget::getFundId, sumAvailable(currency)));
    Map<String, MonetaryAmount> newUnavailableByFundId = newBudgets.stream().collect(groupingBy(Budget::getFundId, sumUnavailable(currency)));

    Map<String, MonetaryAmount> availableDifference = getAmountDifference(oldAvailableByFundId, newAvailableByFundId);

    Map<String, MonetaryAmount> unavailableDifference = getAmountDifference(oldUnavailableByFundId, newUnavailableByFundId);
    return calculateLedgerFyTotals(fundIdsGroupedByLedgerFY, availableDifference, unavailableDifference);
  }

  private Map<String, MonetaryAmount> getAmountDifference(Map<String, MonetaryAmount> oldAvailableByFundId, Map<String, MonetaryAmount> newAvailableByFundId) {
    return oldAvailableByFundId.entrySet().stream()
      .map(entry -> {
        MonetaryAmount diff = entry.getValue().subtract(newAvailableByFundId.get(entry.getKey()));
        entry.setValue(diff);
        return entry;
      })
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Collector<Budget, ?, MonetaryAmount> sumAvailable(String currency) {
    return Collectors.mapping(budget -> Money.of(budget.getAvailable(), currency),
      Collectors.reducing(Money.of(0, currency), MonetaryFunctions::sum));
  }

  private Collector<Budget, ?, MonetaryAmount> sumUnavailable(String currency) {
    return Collectors.mapping(budget -> Money.of(budget.getUnavailable(), currency),
      Collectors.reducing(Money.of(0, currency), MonetaryFunctions::sum));
  }

  private Future<Map<LedgerFY, Set<String>>> getGroupedFundIdsByLedgerFy(List<Budget> budgets, DBClient client) {
    return fundService.getFundsByBudgets(budgets, client)
      .compose(funds -> ledgerFiscalYearService.getLedgerFiscalYearsByBudgets(budgets, client)
        .map(ledgerFYears -> groupFundIdsByLedgerFy(ledgerFYears, funds)));
  }

  private Map<LedgerFY, Set<String>> groupFundIdsByLedgerFy(List<LedgerFY> ledgerFYears, List<Fund> funds) {
    Map<String, Set<String>> fundIdsGroupedByLedgerId = funds.stream()
      .collect(groupingBy(Fund::getLedgerId, HashMap::new, Collectors.mapping(Fund::getId, Collectors.toSet())));
    return ledgerFYears.stream()
      .collect(toMap(identity(), ledgerFY -> fundIdsGroupedByLedgerId.get(ledgerFY.getLedgerId())));
  }

  private List<LedgerFY> calculateLedgerFyTotals(Map<LedgerFY, Set<String>> groupedLedgerFYs, Map<String, MonetaryAmount> availableDifference, Map<String, MonetaryAmount> unavailableDifference) {
    return groupedLedgerFYs.entrySet().stream().map(ledgerFYListEntry -> updateLedgerFY(ledgerFYListEntry, availableDifference, unavailableDifference)).collect(toList());
  }

  private LedgerFY updateLedgerFY(Map.Entry<LedgerFY, Set<String>> ledgerFYListEntry, Map<String, MonetaryAmount> availableDifference, Map<String, MonetaryAmount> unavailableDifference) {
    LedgerFY ledgerFY = ledgerFYListEntry.getKey();

    MonetaryAmount availableAmount = ledgerFYListEntry.getValue().stream()
      .map(availableDifference::get).reduce(MonetaryFunctions::sum)
      .orElse(Money.zero(Monetary.getCurrency(ledgerFY.getCurrency())));

    MonetaryAmount unavailableAmount = ledgerFYListEntry.getValue().stream()
      .map(unavailableDifference::get).reduce(MonetaryFunctions::sum)
      .orElse(Money.zero(Monetary.getCurrency(ledgerFY.getCurrency())));

    double newAvailable = Math.max(Money.of(ledgerFY.getAvailable(), ledgerFY.getCurrency()).subtract(availableAmount).getNumber().doubleValue(), 0d);
    double newUnavailable = Math.max(Money.of(ledgerFY.getUnavailable(), ledgerFY.getCurrency()).subtract(unavailableAmount).getNumber().doubleValue(), 0d);

    return ledgerFY
      .withAvailable(newAvailable)
      .withUnavailable(newUnavailable);
  }

  /**
   * Calculates remaining amount for payment and pending payments
   * [remaining amount] = (allocated * allowableExpenditure) - (allocated - (unavailable + available)) - (awaitingPayment + expended + encumbered)
   *
   * @param budget     processed budget
   * @param transaction
   * @return remaining amount for payment
   */
  protected Money getBudgetRemainingAmount(Budget budget, Transaction transaction) {
    Money allocated = Money.of(budget.getAllocated(), transaction.getCurrency());
    // get allowableExpenditure from percentage value
    double allowableExpenditure = Money.of(budget.getAllowableExpenditure(), transaction.getCurrency()).divide(100d).getNumber().doubleValue();
    Money unavailable = Money.of(budget.getUnavailable(), transaction.getCurrency());
    Money available = Money.of(budget.getAvailable(), transaction.getCurrency());
    Money expenditure = Money.of(budget.getExpenditures(), transaction.getCurrency());
    Money awaitingPayment = Money.of(budget.getAwaitingPayment(), transaction.getCurrency());
    Money encumbered = Money.of(budget.getEncumbered(), transaction.getCurrency());

    Money result = allocated.multiply(allowableExpenditure);
    result = result.subtract(allocated.subtract(unavailable.add(available)));

    if (transaction.getTransactionType().equals(TransactionType.PENDING_PAYMENT)) {
      result = result.subtract(expenditure.add(awaitingPayment));
    }
    else if(transaction.getTransactionType().equals(TransactionType.PAYMENT)) {
      result = result.subtract(expenditure.add(encumbered));
    }
    else {
      result = result.subtract(expenditure.add(awaitingPayment).add(encumbered));
    }

    return result;
  }

  abstract Future<Void> processTemporaryToPermanentTransactions(List<Transaction> transactions, DBClient client);

  abstract String getSummaryId(Transaction transaction);

  abstract Void handleValidationError(Transaction transaction);

  protected abstract String getSelectBudgetsQuery(String tenantId);

  protected abstract boolean isTransactionOverspendRestricted(Ledger ledger, Budget budget);

}
