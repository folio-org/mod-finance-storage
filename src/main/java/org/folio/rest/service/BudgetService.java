package org.folio.rest.service;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
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

public class BudgetService {

  private static final String BUDGET_TABLE = "budget";
  private static final String GROUP_FUND_FY_TABLE = "group_fund_fiscal_year";
  private static final String TRANSACTIONS_TABLE = "transaction";

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final String tenantId;
  private PostgresClient pgClient;

  public BudgetService(Vertx vertx, String tenantId) {
    this.tenantId = tenantId;
    this.pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  public void deleteById(String id, Context vertxContext, Handler<AsyncResult<Response>> asyncResultHandler) {
      vertxContext.runOnContext(v -> {
        Tx<String> tx = new Tx<>(id, pgClient);
        getBudgetById(id)
          .compose(this::checkTransactions)
            .compose(stringTx -> tx.startTx()
            .compose(this::unlinkGroupFundFiscalYears)
            .compose(this::deleteBudget)
            .compose(Tx::endTx)
            .setHandler(reply -> {
              if (reply.failed()) {
                tx.rollbackTransaction();
              }
            }))
          .setHandler(handleNoContentResponse(asyncResultHandler, id, "Budget {} {} deleted"));
      });
  }

  private Future<Tx<String>> deleteBudget(Tx<String> tx) {
    Promise<Tx<String>> promise = Promise.promise();
    pgClient.delete(tx.getConnection(), BUDGET_TABLE, tx.getEntity(), reply -> {
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
          promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(), "transactionIsPresentBudgetDeleteError"));
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

    pgClient.execute(stringTx.getConnection(), sql, queryParams, reply -> {
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
