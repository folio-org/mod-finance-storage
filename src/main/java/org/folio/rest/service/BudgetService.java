package org.folio.rest.service;

import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.dao.BudgetDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class BudgetService {

  private static final String BUDGET_TABLE = "budget";
  private static final String GROUP_FUND_FY_TABLE = "group_fund_fiscal_year";
  private static final String TRANSACTIONS_TABLE = "transaction";
  public static final String TRANSACTION_IS_PRESENT_BUDGET_DELETE_ERROR = "transactionIsPresentBudgetDeleteError";
  public static final String BUDGET_NOT_FOUND_FOR_TRANSACTION = "Budget not found for pair fiscalYear-fundId";

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private BudgetDAO budgetDAO;

  public BudgetService() {
    budgetDAO = new BudgetDAO();
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

  private Future<DBClient> unlinkGroupFundFiscalYears(String id, DBClient client) {
    Promise<DBClient> promise = Promise.promise();

    JsonArray queryParams = new JsonArray();
    queryParams.add(id);
    String sql = "UPDATE "+ getFullTableName(client.getTenantId(), GROUP_FUND_FY_TABLE)  + " SET jsonb = jsonb - 'budgetId' WHERE budgetId=?;";

    client.getPgClient().execute(client.getConnection(), sql, queryParams, reply -> {
      if (reply.failed()) {
        logger.error("Failed to update group_fund_fiscal_year by budgetId={}", reply.cause(), id);
        handleFailure(promise, reply);
      } else {
        promise.complete(client);
      }
    });
    return promise.future();
  }

  public Future<Budget> getBudgetByFundIdAndFiscalYearId(String fiscalYearId, String fundId, Context context, Map<String, String> headers) {
    Promise<Budget> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder()
      .with("fundId", fundId)
      .with("fiscalYearId", fiscalYearId)
      .build();
    DBClient client = new DBClient(context, headers);
    budgetDAO.getBudgets(criterion, client)
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


}
