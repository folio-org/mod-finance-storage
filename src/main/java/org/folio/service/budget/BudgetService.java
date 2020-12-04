package org.folio.service.budget;

import static java.util.stream.Collectors.toList;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getQueryValues;
import static org.folio.rest.util.ErrorCodes.GENERIC_ERROR_CODE;
import static org.folio.rest.util.ErrorCodes.NOT_ENOUGH_MONEY_FOR_ALLOCATION;
import static org.folio.rest.util.ErrorCodes.NOT_ENOUGH_MONEY_FOR_TRANSFER;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.folio.dao.budget.BudgetDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.util.ErrorCodes;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.sqlclient.Tuple;

public class BudgetService {

  private static final String GROUP_FUND_FY_TABLE = "group_fund_fiscal_year";
  private static final String TRANSACTIONS_TABLE = "transaction";
  public static final String TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR = "transactionIsPresentBudgetDeleteError";
  public static final String BUDGET_NOT_FOUND_FOR_TRANSACTION = "Budget not found for pair fiscalYear-fundId";

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private BudgetDAO budgetDAO;

  public BudgetService(BudgetDAO budgetDAO) {
    this.budgetDAO = budgetDAO;
  }

  public void deleteById(String id, Context vertxContext, Map<String, String> headers, Handler<AsyncResult<Response>> asyncResultHandler) {
      vertxContext.runOnContext(v -> {
        DBClient client = new DBClient(vertxContext, headers);
        budgetDAO.getBudgetById(id, client)
          .compose(budget -> checkTransactions(budget, client))
            .compose(aVoid -> client.startTx()
            .compose(t -> unlinkGroupFundFiscalYears(id, client))
            .compose(t -> budgetDAO.deleteBudget(id, client))
            .compose(t -> client.endTx())
            .onComplete(reply -> {
              if (reply.failed()) {
                client.rollbackTransaction();
              }
            }))
          .onComplete(handleNoContentResponse(asyncResultHandler, id, "Budget {} {} deleted"));
      });
  }

  private Future<Void> checkTransactions(Budget budget, DBClient client) {
    Promise<Void> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder("OR")
      .with("fromFundId", budget.getFundId())
      .with("toFundId", budget.getFundId())
      .withOperation("AND")
      .with("fiscalYearId", budget.getFiscalYearId())
      .build();
    criterion.setLimit(new Limit(0));

    client.getPgClient().get(TRANSACTIONS_TABLE, Transaction.class, criterion, true, reply -> {
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

  private Future<Void> unlinkGroupFundFiscalYears(String id, DBClient client) {
    Promise<Void> promise = Promise.promise();

    String sql = "UPDATE "+ getFullTableName(client.getTenantId(), GROUP_FUND_FY_TABLE)  + " SET jsonb = jsonb - 'budgetId' WHERE budgetId=$1;";

    client.getPgClient().execute(client.getConnection(), sql, Tuple.of(UUID.fromString(id)), reply -> {
      if (reply.failed()) {
        logger.error("Failed to update group_fund_fiscal_year by budgetId={}", reply.cause(), id);
        handleFailure(promise, reply);
      } else {
        promise.complete();
      }
    });
    return promise.future();
  }

  public Future<Budget> getBudgetByFundIdAndFiscalYearId(String fiscalYearId, String fundId, DBClient dbClient) {
    Promise<Budget> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder()
      .with("fundId", fundId)
      .with("fiscalYearId", fiscalYearId)
      .build();

    budgetDAO.getBudgets(criterion, dbClient)
      .onComplete(reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else if (reply.result().isEmpty()) {
          logger.error(BUDGET_NOT_FOUND_FOR_TRANSACTION);
          promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), BUDGET_NOT_FOUND_FOR_TRANSACTION));
        } else {
          promise.complete(reply.result().get(0));
        }
      });

    return promise.future();
  }

  public Future<Integer> updateBatchBudgets(Collection<Budget> budgets, DBClient client) {
    return budgetDAO.updateBatchBudgets(buildUpdateBudgetsQuery(budgets, client.getTenantId()), client);
  }

  private String buildUpdateBudgetsQuery(Collection<Budget> budgets, String tenantId) {
    List<JsonObject> jsonBudgets = budgets.stream().map(JsonObject::mapFrom).collect(toList());
    return String.format(
      "UPDATE %s AS budgets SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) WHERE b.id::uuid = budgets.id;",
      getFullTableName(tenantId, BUDGET_TABLE), getQueryValues(jsonBudgets));
  }


  public Future<List<Budget>> getBudgets(String sql, Tuple params, DBClient client) {
    return budgetDAO.getBudgets(sql, params, client);
  }

  public void updateBudgetMetadata(Budget budget, Transaction transaction) {
    budget.getMetadata().setUpdatedDate(transaction.getMetadata().getUpdatedDate());
    budget.getMetadata().setUpdatedByUserId(transaction.getMetadata().getUpdatedByUserId());
  }

  public Future<Void> checkBudgetHaveMoneyForTransaction(Transaction transaction, DBClient client) {
    if (transaction.getFromFundId() == null) {
      return Future.succeededFuture();
    }

    return getBudgetByFundIdAndFiscalYearId(transaction.getFiscalYearId(), transaction.getFromFundId(), client)
      .compose(budget -> {
        if (budget.getAvailable() < transaction.getAmount()) {
          ErrorCodes errorCode;
          switch (transaction.getTransactionType()) {
            case TRANSFER: errorCode = NOT_ENOUGH_MONEY_FOR_TRANSFER; break;
            case ALLOCATION: errorCode = NOT_ENOUGH_MONEY_FOR_ALLOCATION; break;
            default: errorCode = GENERIC_ERROR_CODE;
          }
          logger.error(errorCode.getDescription());
          return Future.failedFuture(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(),
            JsonObject.mapFrom(errorCode.toError()).encodePrettily()));
        }
        return Future.succeededFuture();
      });
  }
}
