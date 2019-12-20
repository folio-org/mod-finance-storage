package org.folio.rest.transaction;

import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.LedgerAPI.LEDGER_TABLE;
import static org.folio.rest.persist.HelperUtils.getCriteriaByFieldNameAndValue;
import static org.folio.rest.persist.HelperUtils.handleFailure;
import static org.folio.rest.persist.HelperUtils.rollbackTransaction;
import static org.folio.rest.persist.HelperUtils.startTx;
import static org.folio.rest.persist.PostgresClient.pojo2json;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
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

  public void createTransaction(Transaction transaction) {
    processAllOrNothing(transaction, this::createPermanentTransactions).setHandler(result -> {
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

  abstract String getSummaryId(Transaction transaction);

  abstract Criterion getTransactionBySummaryIdCriterion(String value);

  abstract void handleValidationError(Transaction transaction);

  abstract String createTempTransactionQuery();

  abstract String createPermanentTransactionsQuery();

  private Future<Tx<List<Transaction>>> createPermanentTransactions(Tx<List<Transaction>> tx) {
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
        .compose(budget -> verifyEncumbranceRestrictions(transaction, budget))
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
    Criteria criteria = getCriteriaByFieldNameAndValue("fundId", "=", transaction.getFromFundId());
    Criteria criteria1 = getCriteriaByFieldNameAndValue("fiscalYearId", "=", transaction.getFiscalYearId());
    Criterion criterion = new Criterion();
    criterion.addCriterion(criteria, "AND", criteria1);
    getPostgresClient().get(BUDGET_TABLE, Budget.class, criterion,true, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else if (reply.result().getResults().isEmpty()) {
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
    String query = "fund.id"+"="+ transaction.getFromFundId();
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

  private Future<Void> verifyEncumbranceRestrictions(Transaction transaction, Budget budget) {
    Promise<Void> promise = Promise.promise();
    if (transaction.getTransactionType() == Transaction.TransactionType.ENCUMBRANCE) {
      if (budget.getBudgetStatus() != Budget.BudgetStatus.ACTIVE) {
        log.error(BUDGET_IS_INACTIVE);
        promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), BUDGET_IS_INACTIVE));
      } else {
        return checkLedgerRestrictions(transaction, budget);
      }
    } else {
      promise.complete();
    }
    return promise.future();
  }

  private Future<Void> checkLedgerRestrictions(Transaction transaction, Budget budget) {
    Promise<Void> promise = Promise.promise();
    getExistentLedger(transaction).setHandler(result -> {
      if (result.succeeded()) {
        if (result.result().getRestrictEncumbrance() && budget.getAllowableEncumbrance() != null) {
          Double remainingAmountForEncumbrance = getBudgetRemainingAmountForEncumbrance(budget, transaction.getCurrency());
          if (remainingAmountForEncumbrance < transaction.getAmount()) {
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

  /**
   * Calculates remaining amount for encumbrance
   * [remaining amount] = (allocated * allowableEncumbered) - (allocated - (unavailable + available)) - (encumbered + awaitingPayment)
   *
   * @param budget     processed budget
   * @param currency   processed transaction currency
   * @return remaining amount for encumbrance
   */
  private Double getBudgetRemainingAmountForEncumbrance(Budget budget, String currency) {
    Money allocated = Money.of(budget.getAllocated(), currency);
    // get allowableEncumbered converted from percentage value
    Double allowableEncumbered = Money.of(budget.getAllowableEncumbrance(), currency).divide(100d).getNumber().doubleValue();
    Money unavailable = Money.of(budget.getUnavailable(), currency);
    Money available = Money.of(budget.getAvailable(), currency);
    Money encumbered = Money.of(budget.getEncumbered(), currency);
    Money awaitingPayment = Money.of(budget.getAwaitingPayment(), currency);

    Money result = allocated.multiply(allowableEncumbered);
    result = result.subtract(allocated.subtract(unavailable.add(available)));
    result = result.subtract(encumbered.add(awaitingPayment));

    return result.getNumber().doubleValue();
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
