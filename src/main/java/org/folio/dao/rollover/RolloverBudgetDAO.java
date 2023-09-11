package org.folio.dao.rollover;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.folio.rest.util.ResponseUtils.handleFailure;

public class RolloverBudgetDAO {

  private static final Logger logger = LogManager.getLogger(RolloverBudgetDAO.class);

  public static final String ROLLOVER_BUDGET_TABLE = "ledger_fiscal_year_rollover_budget";

  public Future<List<LedgerFiscalYearRolloverBudget>> getRolloverBudgets(Criterion filter, DBClient client) {
    logger.debug("Trying to get rollover budgets by query: {}", filter);
    Promise<List<LedgerFiscalYearRolloverBudget>> promise = Promise.promise();
    client.getPgClient()
      .get(ROLLOVER_BUDGET_TABLE, LedgerFiscalYearRolloverBudget.class, filter, true, reply -> {
        if (reply.failed()) {
          logger.error("Getting rollover budgets by query: {} failed", filter, reply.cause());
          handleFailure(promise, reply);
        } else {
          logger.info("Successfully retrieved {} rollover budgets by query: {}", reply.result().getResults().size(), filter);
          promise.complete(reply.result().getResults());
        }
      });
    return promise.future();
  }

  public Future<List<LedgerFiscalYearRolloverBudget>> updateBatch(List<LedgerFiscalYearRolloverBudget> rolloverBudgets, DBClient client) {
    logger.debug("Trying to batch update rollover budgets");
    Promise<List<LedgerFiscalYearRolloverBudget>> promise = Promise.promise();
    client.getPgClient()
      .updateBatch(ROLLOVER_BUDGET_TABLE, rolloverBudgets, reply -> {
        if (reply.failed()) {
          logger.error("Updating rollover budgets failed", reply.cause());
          handleFailure(promise, reply);
        } else {
          List<LedgerFiscalYearRolloverBudget> budgets = new ArrayList<>();
          if (Objects.nonNull(reply.result())) {
            reply.result()
              .spliterator()
              .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(LedgerFiscalYearRolloverBudget.class)));
          }
          logger.info("Successfully updated {} rollover budgets", budgets.size());
          promise.complete(budgets);
        }
    });
    return promise.future();
  }

  public Future<LedgerFiscalYearRolloverBudget> updateRolloverBudget(LedgerFiscalYearRolloverBudget rolloverBudget, DBClient client) {
    logger.debug("Trying to update rollover budget with id {}", rolloverBudget.getId());
    Promise<LedgerFiscalYearRolloverBudget> promise = Promise.promise();
    client.getPgClient().
      update(ROLLOVER_BUDGET_TABLE, JsonObject.mapFrom(rolloverBudget), rolloverBudget.getId(), reply -> {
        if (reply.failed()) {
          logger.error("Updating rollover budget with id {} failed", rolloverBudget.getId(), reply.cause());
          handleFailure(promise, reply);
        } else {
          logger.info("Successfully updated a rollover budget with id {}", rolloverBudget.getId());
          promise.complete(rolloverBudget);
        }
      });
    return promise.future();
  }

  public Future<Void> deleteByQuery(Criterion filter, DBClient client) {
    logger.debug("Trying to delete rollover budgets by query: {}", filter);
    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .delete(ROLLOVER_BUDGET_TABLE, filter, reply -> {
        if (reply.failed()) {
          logger.error("Deleting rollover budgets by query = {} failed", filter, reply.cause());
          handleFailure(promise, reply);
        } else {
          logger.info("Successfully deleted rollover budgets by query: {}", filter);
          promise.complete();
        }
      });
    return promise.future();
  }
}
