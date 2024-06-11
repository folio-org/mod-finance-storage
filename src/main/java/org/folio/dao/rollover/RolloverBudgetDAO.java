package org.folio.dao.rollover;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.interfaces.Results;

import java.util.ArrayList;
import java.util.List;

public class RolloverBudgetDAO {

  private static final Logger logger = LogManager.getLogger(RolloverBudgetDAO.class);

  public static final String ROLLOVER_BUDGET_TABLE = "ledger_fiscal_year_rollover_budget";

  public Future<List<LedgerFiscalYearRolloverBudget>> getRolloverBudgets(Criterion filter, DBConn conn) {
    logger.debug("Trying to get rollover budgets by query: {}", filter);
    return conn.get(ROLLOVER_BUDGET_TABLE, LedgerFiscalYearRolloverBudget.class, filter, true)
      .map(Results::getResults)
      .onSuccess(budgets -> logger.info("Successfully retrieved {} rollover budgets by query: {}", budgets.size(), filter))
      .onFailure(e -> logger.error("Getting rollover budgets by query: {} failed", filter, e));
  }

  public Future<List<LedgerFiscalYearRolloverBudget>> updateBatch(List<LedgerFiscalYearRolloverBudget> rolloverBudgets, DBConn conn) {
    logger.debug("Trying to batch update rollover budgets");
    return conn.updateBatch(ROLLOVER_BUDGET_TABLE, rolloverBudgets)
      .map(rowSet -> {
        List<LedgerFiscalYearRolloverBudget> budgets = new ArrayList<>();
        if (rowSet != null) {
          rowSet.spliterator().forEachRemaining(row ->
            budgets.add(row.get(JsonObject.class, 0).mapTo(LedgerFiscalYearRolloverBudget.class)));
        }
        return budgets;
      })
      .onSuccess(budgets -> logger.info("Successfully updated {} rollover budgets", budgets.size()))
      .onFailure(e -> logger.error("Updating rollover budgets failed", e));
  }

  public Future<Void> updateRolloverBudget(LedgerFiscalYearRolloverBudget rolloverBudget, DBConn conn) {
    logger.debug("Trying to update rollover budget with id {}", rolloverBudget.getId());
    return conn.update(ROLLOVER_BUDGET_TABLE, rolloverBudget, rolloverBudget.getId())
      .onSuccess(rowSet -> logger.info("Successfully updated a rollover budget with id {}", rolloverBudget.getId()))
      .onFailure(e -> logger.error("Updating rollover budget with id {} failed", rolloverBudget.getId(), e))
      .mapEmpty();
  }

  public Future<Void> deleteByQuery(Criterion filter, DBConn conn) {
    logger.debug("Trying to delete rollover budgets by query: {}", filter);
    return conn.delete(ROLLOVER_BUDGET_TABLE, filter)
      .onSuccess(rowSet -> logger.info("Successfully deleted rollover budgets by query: {}", filter))
      .onFailure(e -> logger.error("Deleting rollover budgets by query = {} failed", filter, e))
      .mapEmpty();
  }
}
