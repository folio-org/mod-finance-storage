package org.folio.rest.service;

import static java.util.stream.Collectors.toList;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FinanceStorageAPI.LEDGERFY_TABLE;
import static org.folio.rest.impl.LedgerAPI.LEDGER_TABLE;
import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import org.folio.rest.dao.BudgetDAO;
import org.folio.rest.dao.TemporaryTransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.tools.utils.TenantTool;
import org.javamoney.moneta.Money;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public abstract class AllOrNothingTransactionService extends AbstractTransactionService {

  public static final String LEDGER_NOT_FOUND_FOR_TRANSACTION = "Ledger not found for transaction";
  public static final String TRANSACTION_SUMMARY_NOT_FOUND_FOR_TRANSACTION = "Transaction summary not found for transaction";
  public static final String ALL_EXPECTED_TRANSACTIONS_ALREADY_PROCESSED = "All expected transactions already processed";

  private TransactionSummaryService transactionSummaryService;
  private BudgetService budgetService;
  private TemporaryTransactionDAO temporaryTransactionDAO;

  AllOrNothingTransactionService(TemporaryTransactionDAO temporaryTransactionDAO) {
    this.temporaryTransactionDAO = temporaryTransactionDAO;

  }

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, Context context, Map<String, String> headers) {
    Promise<Transaction> promise = Promise.promise();
    processAllOrNothing(transaction, this::processTemporaryToPermanentTransactions).onComplete(result -> {
      if (result.failed()) {
        HttpStatusException cause = (HttpStatusException) result.cause();
        handleFailure(promise, result);
//        switch (cause.getStatusCode()) {
//          case 400:
//            getAsyncResultHandler().handle(Future.succeededFuture(
//              FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.respond400WithTextPlain(cause.getPayload())));
//            break;
//          case 422:
//            getAsyncResultHandler().handle(Future.succeededFuture(
//              FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.respond422WithApplicationJson(new JsonObject(cause.getPayload()).mapTo(Errors.class))
//            ));
//            break;
//          default:
//            getAsyncResultHandler()
//              .handle(Future.succeededFuture(FinanceStorageTransactions.PostFinanceStorageTransactionsResponse
//                .respond500WithTextPlain(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())));
//      }
      } else {
//        log.debug("Preparing response to client");
//        getAsyncResultHandler().handle(
//            Future.succeededFuture(FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.respond201WithApplicationJson(
//                transaction, FinanceStorageTransactions.PostFinanceStorageTransactionsResponse.headersFor201()
//                  .withLocation(TRANSACTION_LOCATION_PREFIX + transaction.getId()))));
      promise.complete(transaction);
      }
    });
    return promise.future();
  }

  abstract Future<Tx<List<Transaction>>> processTemporaryToPermanentTransactions(Tx<List<Transaction>> tx);

  abstract String getSummaryId(Transaction transaction);

  abstract Criterion getTransactionBySummaryIdCriterion(String value);

  abstract void handleValidationError(Transaction transaction);

  abstract String createPermanentTransactionsQuery();

  protected String createPermanentTransactionsQuery(String sql) {
    return String.format(sql, getFullTransactionTableName(), getTemporaryTransactionTable());
  }

  protected abstract String getSelectBudgetsQuery();

  protected String getSelectBudgetsQuery(String sql) {
    return String.format(sql, getFullTableName(getTenantId(), BUDGET_TABLE), getFullTemporaryTransactionTableName());
  }

  protected Future<Integer> createPermanentTransactions(Tx<List<Transaction>> tx) {
    Promise<Integer> promise = Promise.promise();
    List<Transaction> transactions = tx.getEntity();
    JsonArray param = new JsonArray();
    param.add(getSummaryId(transactions.get(0)));
    tx.getPgClient()
      .execute(tx.getConnection(), createPermanentTransactionsQuery(), param, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(reply.result().getUpdated());
        }
      });
    return promise.future();
  }

  /**
   * Accumulate transactions in a temporary table until expected number of transactions are present, then apply them all at once,
   * updating all the required tables together in a database transaction.
   *
   * @param transaction         processed transaction
   *                            permanent one.
   * @return completed future
   */
  Future<Void> processAllOrNothing(Transaction transaction, Context context, Map<String, String> headers) {
    try {
      handleValidationError(transaction);
      return budgetService.verifyBudgetHasEnoughMoney(transaction, context, headers)
        .compose(v -> getAndCheckTransactionSummary(transaction)
          .compose(summary -> addTempTransactionSequentially(transaction, summary, context.owner(), TenantTool.tenantId(headers))
            .compose(transactions -> {
              Promise<Void> promise = Promise.promise();
              try {
                if (transactions.size() == getNumTransactions(summary, transaction)) {
                  return moveFromTempToPermanentTable(transactions, summary);
                } else {
                  promise.complete();
                }
              } catch (Exception e) {
                promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
              }
              return promise.future();
          })));
    } catch (HttpStatusException e) {
      return Future.failedFuture(e);
    }
  }


  private Future<Void> moveFromTempToPermanentTable(List<Transaction> transactions,
      JsonObject summary) {

    Promise<Void> promise = Promise.promise();
    Tx<List<Transaction>> tx = new Tx<>(transactions, getPostgresClient());

    tx.startTx()
      .compose(this::processTemporaryToPermanentTransactions)
      .compose(this::deleteTempTransactions)
      .compose(tr -> transactionSummaryService.setTransactionsSummariesProcessed(tx, summary, summaryTable))
      .compose(Tx::endTx)
      .onComplete(result -> {
        if (result.failed()) {
          log.error("Transactions {} or associated data failed to be processed", result.cause(), tx.getEntity());

          tx.rollbackTransaction()
            .onComplete(res -> handleFailure(promise, result));
        } else {
          log.info("Transactions {} and associated data were successfully processed", tx.getEntity());
          promise.complete();
        }
      });

    return promise.future();
  }


  private Future<List<Transaction>> addTempTransactionSequentially(Transaction transaction, JsonObject summary, Vertx vertx, String tenantId) {
    Promise<List<Transaction>> promise = Promise.promise();
    SharedData sharedData = vertx.sharedData();
    // define unique lockName based on combination of transactions type and summary id
    String lockName = transaction.getTransactionType() + getSummaryId(transaction);

    sharedData.getLock(lockName, lockResult -> {
      if (lockResult.succeeded()) {
        log.info("Got lock {}", lockName);
        Lock lock = lockResult.result();
        try {
          vertx.setTimer(30000, timerId -> releaseLock(lock, lockName));

          temporaryTransactionDAO.createTempTransaction(transaction, getSummaryId(transaction), vertx, tenantId)
            .compose(tr -> temporaryTransactionDAO.getTempTransactionsBySummaryId(summary.getString(ID_FIELD_NAME), vertx, tenantId))
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
        }
      } else {
        promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), lockResult.cause().getMessage()));
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

  protected Future<Tx<List<Transaction>>> updateBudgets(Tx<List<Transaction>> tx, List<Budget> budgets) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    List<JsonObject> jsonBudgets = budgets.stream().map(JsonObject::mapFrom).collect(toList());
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

  protected Future<Tx<List<Transaction>>> updatePermanentTransactions(Tx<List<Transaction>> tx, List<Transaction> transactions) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    if (transactions.isEmpty()) {
      promise.complete(tx);
    } else {
      List<JsonObject> jsonTransactions = transactions.stream().map(JsonObject::mapFrom).collect(Collectors.toList());
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
    String sql = getSelectBudgetsQuery();
    JsonArray params = new JsonArray();
    params.add(getSummaryId(tx.getEntity().get(0)));
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

  Future<Tx<List<Transaction>>> updateLedgerFYs(Tx<List<Transaction>> tx, List<LedgerFY> ledgerFYs) {
    Promise<Tx<List<Transaction>>> promise = Promise.promise();
    if (ledgerFYs.isEmpty()) {
      promise.complete(tx);
    } else {
      String sql = getLedgerFyUpdateQuery(ledgerFYs);
      tx.getPgClient().execute(tx.getConnection(), sql, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(tx);
        }
      });
    }
    return promise.future();
  }

  private String getLedgerFyUpdateQuery(List<LedgerFY> ledgerFYs) {
    List<JsonObject> jsonLedgerFYs = ledgerFYs.stream().map(JsonObject::mapFrom).collect(toList());
    return String.format("UPDATE %s AS ledger_fy SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) "
      + "WHERE b.id::uuid = ledger_fy.id;", getFullTableName(getTenantId(), LEDGERFY_TABLE), getValues(jsonLedgerFYs));
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

  private void releaseLock(Lock lock, String lockName) {
    log.info("Released lock {}", lockName);
    lock.release();
  }

}
