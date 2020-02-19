package org.folio.rest.transaction;

import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.LedgerAPI.LEDGER_TABLE;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.handleFailure;
import static org.folio.rest.persist.HelperUtils.rollbackTransaction;
import static org.folio.rest.persist.HelperUtils.startTx;
import static org.folio.rest.persist.PostgresClient.pojo2json;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.javamoney.moneta.Money;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public abstract class AllOrNothingHandler extends AbstractTransactionHandler {

  public static final String BUDGET_NOT_FOUND_FOR_TRANSACTION = "Budget not found for transaction";
  public static final String LEDGER_NOT_FOUND_FOR_TRANSACTION = "Ledger not found for transaction";
  public static final String TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION = "Transaction summary not found for transaction";
  public static final String BUDGET_IS_INACTIVE = "Cannot create encumbrance from the not active budget";
  public static final String FUND_CANNOT_BE_PAID = "Fund cannot be paid due to restrictions";
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

  @Override
  public void createTransaction(Transaction transaction) {
    processAllOrNothing(transaction, this::processTemporaryToPermanentTransactions).setHandler(result -> {
      if (result.failed()) {
        HttpStatusException cause = (HttpStatusException) result.cause();
        switch (cause.getStatusCode()) {
          case 400:
            getAsyncResultHandler().handle(Future.succeededFuture(
              FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.respond400WithTextPlain(cause.getPayload())));
            break;
          case 422:
            getAsyncResultHandler().handle(Future.succeededFuture(
              FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.respond422WithApplicationJson(new JsonObject(cause.getPayload()).mapTo(Errors.class))
            ));
            break;
          default:
            getAsyncResultHandler()
              .handle(Future.succeededFuture(FinanceStorageTransactions.PostFinanceStorageTransactionsResponse
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

  abstract Future<Tx<List<Transaction>>> processTemporaryToPermanentTransactions(Tx<List<Transaction>> tx);

  abstract String getSummaryId(Transaction transaction);

  abstract Criterion getTransactionBySummaryIdCriterion(String value);

  abstract void handleValidationError(Transaction transaction);

  abstract String createTempTransactionQuery();

  abstract String createPermanentTransactionsQuery();

  protected Future<Tx<List<Transaction>>> createPermanentTransactions(Tx<List<Transaction>> tx) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    List<Transaction> transactions = tx.getEntity();
    JsonArray param = new JsonArray();
    param.add(getSummaryId(transactions.get(0)));
    tx.getPgClient()
      .execute(tx.getConnection(), createPermanentTransactionsQuery(), param, reply -> {
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
    try {
      handleValidationError(transaction);
      return getExistentBudget(transaction)
        .compose(budget -> checkTransactionRestrictions(transaction, budget))
        .compose(v -> createTempTransaction(transaction))
        .compose(this::getSummary)
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
    } catch (HttpStatusException e) {
      return Future.failedFuture(e);
    }

  }

  protected Future<Budget> getExistentBudget(Transaction transaction) {
    Promise<Budget> promise = Promise.promise();
    Criteria criteria = getCriteriaByFieldNameAndValue("fundId", "=", transaction.getTransactionType()
      .equals(Transaction.TransactionType.CREDIT) ? transaction.getToFundId() : transaction.getFromFundId());
    Criteria criteria1 = getCriteriaByFieldNameAndValue("fiscalYearId", "=", transaction.getFiscalYearId());
    Criterion criterion = new Criterion();
    criterion.addCriterion(criteria, "AND", criteria1);
    getPostgresClient().get(BUDGET_TABLE, Budget.class, criterion, false, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else if (reply.result()
        .getResults()
        .isEmpty()) {
        log.error(BUDGET_NOT_FOUND_FOR_TRANSACTION);
        promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), BUDGET_NOT_FOUND_FOR_TRANSACTION));
      } else {
        promise.complete(reply.result().getResults().get(0));
      }
    });
    return promise.future();
  }

  private Future<Ledger> getExistentLedger(Transaction transaction) {
    Promise<Ledger> promise = Promise.promise();
    String fundId = transaction.getTransactionType() == Transaction.TransactionType.CREDIT ? transaction.getToFundId() : transaction.getFromFundId();
    String query = "fund.id"+"=" + fundId;
    PgUtil.get(LEDGER_TABLE, Ledger.class, LedgerCollection.class, query , 0, 1, getOkapiHeaders(), getVertxContext(),
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

      try {
        JsonArray params = new JsonArray();
        params.add(transaction.getId());
        params.add(pojo2json(transaction));

        getPostgresClient().execute(createTempTransactionQuery(), params, reply -> {
          if (reply.failed()) {
            log.error("Transaction creation with id={} failed", reply.cause(), transaction.getId());
            handleFailure(promise, reply);
          } else {
            log.debug("New transaction with id={} successfully created", transaction.getId());
            promise.complete(transaction);
          }
        });
      } catch (Exception e) {
        promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
      }

    return promise.future();
  }

  private Future<Pair<String, Integer>> getSummary(Transaction transaction) {
    Promise<Pair<String, Integer>> promise = Promise.promise();

    log.debug("Get summary={}", getSummaryId(transaction));

    getPostgresClient().getById(summaryTable, getSummaryId(transaction), reply -> {
      if (reply.failed()) {
        log.error("Summary retrieval with id={} failed", reply.cause(), transaction.getId());
        handleFailure(promise, reply);
      } else {
        final JsonObject summary = reply.result();
        if (summary == null) {
          promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION));
        } else {
          log.debug("Summary with id={} successfully extracted", transaction.getId());
          Pair<String, Integer> result = new ImmutablePair<>(summary.getString("id"), getNumTransactions(summary, transaction));
          promise.complete(result);
        }
      }
    });
    return promise.future();
  }

  private Integer getNumTransactions(JsonObject summary, Transaction transaction) {
    if (Transaction.TransactionType.ENCUMBRANCE == transaction.getTransactionType()) {
      return summary.getInteger("numEncumbrances") != null ? summary.getInteger("numEncumbrances") : summary.getInteger("numTransactions");
    } else {
      return summary.getInteger("numPaymentsCredits");
    }
  }

  private Future<List<Transaction>> getTempTransactions(Pair<String, Integer> summary) {
    Promise<List<Transaction>> promise = Promise.promise();

    Criterion criterion = getTransactionBySummaryIdCriterion(summary.getKey());

    getPostgresClient().get(temporaryTransactionTable, Transaction.class, criterion, false, false, reply -> {
      if (reply.failed()) {
        log.error("Failed to extract temporary transaction by summary id={}", reply.cause(), summary.getKey());
        handleFailure(promise, reply);
      } else {
        List<Transaction> transactions = reply.result()
          .getResults();

        isLastRecord = transactions.size() == summary.getValue();
        promise.complete(transactions);
      }
    });
    return promise.future();
  }


  protected Future<Tx<List<Transaction>>> updateBudgets(Tx<List<Transaction>> tx, List<Budget> budgets) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    List<JsonObject> jsonBudgets = budgets.stream().map(JsonObject::mapFrom).collect(Collectors.toList());
    String sql = buildUpdateBudgetsQuery(jsonBudgets);
    tx.getPgClient().execute(tx.getConnection(), sql, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  private String buildUpdateBudgetsQuery(List<JsonObject> jsonBudgets) {
    return String.format(
        "UPDATE %s AS budgets SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) WHERE b.id::uuid = budgets.id;",
        getFullTableName(getTenantId(), BUDGET_TABLE), getValues(jsonBudgets));
  }


  protected Future<Tx<List<Transaction>>> updatePermanentTransactions(Tx<List<Transaction>> tx) {
    return updatePermanentTransactions(tx, tx.getEntity());
  }

  protected Future<Tx<List<Transaction>>> updatePermanentTransactions(Tx<List<Transaction>> tx, List<Transaction> transactions) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    List<JsonObject> jsonTransactions = transactions.stream().map(JsonObject::mapFrom).collect(Collectors.toList());
    if (transactions.isEmpty()) {
      promise.complete(tx);
    } else {
      String sql = buildUpdatePermanentTransactionQuery(jsonTransactions);
      tx.getPgClient()
        .execute(tx.getConnection(), sql, reply -> {
          if (reply.failed()) {
            handleFailure(promise, reply);
          } else {
            promise.complete(tx);
          }
        });
    }
    return promise.future();
  }

  private String buildUpdatePermanentTransactionQuery(List<JsonObject> transactions) {
    return String.format("UPDATE %s AS transactions " +
      "SET jsonb = t.jsonb FROM (VALUES  %s) AS t (id, jsonb) " +
      "WHERE t.id::uuid = transactions.id;", getFullTransactionTableName(), getValues(transactions));
  }

  protected Future<List<Budget>> getBudgets(Tx<List<Transaction>> tx) {
    Promise<List<Budget>> promise = Promise.promise();
    String sql = getBudgetsQuery();
    JsonArray params = new JsonArray();
    params.add(getSummaryId(tx.getEntity()
      .get(0)));
    tx.getPgClient()
      .select(tx.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Budget> budgets = reply.result()
            .getResults()
            .stream()
            .flatMap(JsonArray::stream)
            .map(o -> new JsonObject(o.toString()).mapTo(Budget.class))
            .collect(Collectors.toList());
          promise.complete(budgets);
        }
      });
    return promise.future();
  }


  protected abstract String getBudgetsQuery();

  protected String getValues(List<JsonObject> entities) {
    return entities.stream().map(entity -> "('" + entity.getString("id") + "', '" + entity.encode() + "'::json)").collect(Collectors.joining(","));
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

  private Future<Void> checkTransactionRestrictions(Transaction transaction, Budget budget) {
      if (budget.getBudgetStatus() != Budget.BudgetStatus.ACTIVE) {
        log.error(BUDGET_IS_INACTIVE);
        throw new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), BUDGET_IS_INACTIVE);
      } else {
        return checkLedgerRestrictions(transaction, budget);
      }
  }

  private Future<Void> checkLedgerRestrictions(Transaction transaction, Budget budget) {
    Promise<Void> promise = Promise.promise();
    getExistentLedger(transaction).setHandler(result -> {
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
        promise.fail(result.cause());
      }
    });
    return promise.future();
  }

  private boolean isEncumbranceRestricted(Transaction transaction, Budget budget, Ledger ledger) {
    return transaction.getTransactionType() == Transaction.TransactionType.ENCUMBRANCE && ledger.getRestrictEncumbrance()
        && budget.getAllowableEncumbrance() != null;
  }

  private boolean isPaymentRestricted(Transaction transaction, Budget budget, Ledger ledger) {
    return transaction.getTransactionType() == Transaction.TransactionType.PAYMENT && ledger.getRestrictExpenditures()
      && budget.getAllowableExpenditure() != null;
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
   * [remaining amount] = (allocated * allowableExpenditure) - (allocated - (unavailable + available)) - (awaitingPayment + expended + encumbered)
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
    Money encumbered = Money.of(budget.getEncumbered(), currency);

    Money result = allocated.multiply(allowableExpenditure);
    result = result.subtract(allocated.subtract(unavailable.add(available)));
    result = result.subtract(expenditure.add(awaitingPayment).add(encumbered));

    return result;
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

}
