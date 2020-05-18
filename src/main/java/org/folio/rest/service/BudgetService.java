package org.folio.rest.service;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.rest.impl.LedgerAPI.LEDGER_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import javax.ws.rs.core.Response;

import org.folio.rest.dao.BudgetDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.tools.utils.TenantTool;
import org.javamoney.moneta.Money;

import java.util.Map;

public class BudgetService {

  private static final String BUDGET_TABLE = "budget";
  private static final String GROUP_FUND_FY_TABLE = "group_fund_fiscal_year";
  private static final String TRANSACTIONS_TABLE = "transaction";
  public static final String TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR = "transactionIsPresentBudgetDeleteError";
  public static final String BUDGET_IS_INACTIVE = "Cannot create encumbrance from the not active budget";
  public static final String FUND_CANNOT_BE_PAID = "Fund cannot be paid due to restrictions";

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final String tenantId;
  private PostgresClient pgClient;
  private BudgetDAO budgetDAO;

  public BudgetService(Vertx vertx, String tenantId) {
    this.tenantId = tenantId;
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  public void deleteById(String id, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
      vertxContext.runOnContext(v -> {
        Tx<String> tx = new Tx<>(id, pgClient);
        getBudgetById(id)
          .compose(this::checkTransactions)
            .compose(aVoid -> tx.startTx()
            .compose(this::unlinkGroupFundFiscalYears)
            .compose(this::deleteBudget)
            .compose(Tx::endTx)
            .onComplete(reply -> {
              if (reply.failed()) {
                tx.rollbackTransaction();
              }
            }))
          .onComplete(handleNoContentResponse(asyncResultHandler, id, "Budget {} {} deleted"));
      });
  }

  public Future<Void> verifyBudgetHasEnoughMoney(Transaction transaction, Context context, Map<String, String> headers) {
    String fundId = transaction.getTransactionType() == Transaction.TransactionType.CREDIT ? transaction.getToFundId() : transaction.getFromFundId();
    PostgresClient client = PostgresClient.getInstance(context.owner(), TenantTool.tenantId(headers));
    return budgetDAO.getBudgetByFundIdAndFiscalYearId(transaction.getFiscalYearId(), fundId, client)
      .compose(budget -> checkTransactionRestrictions(transaction, budget));
  }


  private Future<Void> checkTransactionRestrictions(Transaction transaction, Budget budget) {
    Promise<Void> promise = Promise.promise();

    if (budget.getBudgetStatus() != Budget.BudgetStatus.ACTIVE) {
      logger.error(BUDGET_IS_INACTIVE, budget.getId());
      promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), BUDGET_IS_INACTIVE));
    } else {
      return checkLedgerRestrictions(transaction, budget);
    }
    return promise.future();
  }

  private Future<Void> checkLedgerRestrictions(Transaction transaction, Budget budget) {
    Promise<Void> promise = Promise.promise();
    getExistentLedger(transaction).onComplete(result -> {
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

  private Future<Ledger> getExistentLedger(Transaction transaction) {
    Promise<Ledger> promise = Promise.promise();
    String fundId = transaction.getTransactionType() == Transaction.TransactionType.CREDIT ? transaction.getToFundId() : transaction.getFromFundId();
    String query = "fund.id =" + fundId;
    PgUtil.get(LEDGER_TABLE, Ledger.class, LedgerCollection.class, query , 0, 1, getOkapiHeaders(), getVertxContext(),
      FinanceStorageLedgers.GetFinanceStorageLedgersResponse.class, reply-> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else if (((LedgerCollection) reply.result().getEntity()).getLedgers().isEmpty()) {
          logger.error(LEDGER_NOT_FOUND_FOR_TRANSACTION);
          promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), LEDGER_NOT_FOUND_FOR_TRANSACTION));
        } else {
          promise.complete(((LedgerCollection) reply.result().getEntity()).getLedgers().get(0));
        }
      });
    return promise.future();
  }

  private Future<Tx<String>> deleteBudget(Tx<String> tx) {
    Promise<Tx<String>> promise = Promise.promise();
    tx.getPgClient().delete(tx.getConnection(), BUDGET_TABLE, tx.getEntity(), reply -> {
      if (reply.result().getUpdated() == 0) {
        promise.fail(new HttpStatusException(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase()));
      } else {
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  private Future<Budget> getBudgetById(String id) {
    Promise<Budget> promise = Promise.promise();

    logger.debug("Get budget={}", id);

    pgClient.getById(BUDGET_TABLE, id, reply -> {
      if (reply.failed()) {
        logger.error("Budget retrieval with id={} failed", reply.cause(), id);
        handleFailure(promise, reply);
      } else {
        final JsonObject budget = reply.result();
        if (budget == null) {
          promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          logger.debug("Budget with id={} successfully extracted", id);
          promise.complete(budget.mapTo(Budget.class));
        }
      }
    });
    return promise.future();
  }

  private Future<Void> checkTransactions(Budget budget) {
    Promise<Void> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder("OR")
      .with("fromFundId", budget.getFundId())
      .with("toFundId", budget.getFundId())
      .withOperation("AND")
      .with("fiscalYearId", budget.getFiscalYearId())
      .build();
    criterion.setLimit(new Limit(0));

    pgClient.get(TRANSACTIONS_TABLE, Transaction.class, criterion, true, reply -> {
      if (reply.failed()) {
        logger.error("Transaction retrieval by query {} failed", reply.cause(), criterion.toString());
        handleFailure(promise, reply);
      } else {
        if (reply.result().getResultInfo().getTotalRecords() > 0) {
          promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR));
        }
        promise.complete();
      }
    });
    return promise.future();
  }

  private Future<Tx<String>> unlinkGroupFundFiscalYears(Tx<String> stringTx) {
    Promise<Tx<String>> promise = Promise.promise();

    JsonArray queryParams = new JsonArray();
    queryParams.add(stringTx.getEntity());
    String sql = "UPDATE "+ getFullTableName(tenantId, GROUP_FUND_FY_TABLE)  + " SET jsonb = jsonb - 'budgetId' WHERE budgetId=?;";

    stringTx.getPgClient().execute(stringTx.getConnection(), sql, queryParams, reply -> {
      if (reply.failed()) {
        logger.error("Failed to update group_fund_fiscal_year by budgetId={}", reply.cause(), stringTx.getEntity());
        handleFailure(promise, reply);
      } else {
        promise.complete(stringTx);
      }
    });
    return promise.future();
  }


}
