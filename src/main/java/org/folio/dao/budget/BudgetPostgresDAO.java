package org.folio.dao.budget;

import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.util.ErrorCodes.BUDGET_EXPENSE_CLASS_REFERENCE_ERROR;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import io.vertx.sqlclient.SqlResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.utils.CalculationUtils;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.rest.exception.HttpException;
import io.vertx.sqlclient.Tuple;

public class BudgetPostgresDAO implements BudgetDAO {

  private static final Logger logger = LogManager.getLogger();

  @Override
  public Future<Void> createBatchBudgets(List<Budget> budgets, DBConn conn) {
    List<String> ids = budgets.stream().map(Budget::getId).toList();
    logger.debug("Trying create batch budgets, ids={}", ids);
    return conn.saveBatch(BUDGET_TABLE, budgets)
      .onSuccess(rowSet -> logger.info("createBatchBudgets:: Created {} batch budgets", budgets.size()))
      .onFailure(e -> logger.error("Create batch budgets failed, ids={}", ids, e))
      .mapEmpty();
  }

  @Override
  public Future<Void> updateBatchBudgets(List<Budget> budgets, DBConn conn) {
    List<String> ids = budgets.stream().map(Budget::getId).toList();
    logger.debug("Trying update batch budgets, ids={}", ids);
    return conn.updateBatch(BUDGET_TABLE, budgets)
      .onSuccess(rowSet -> logger.info("updateBatchBudgets:: Updated {} batch budgets", budgets.size()))
      .onFailure(e -> logger.error("Update batch budgets failed, ids={}", ids, e))
      .mapEmpty();
  }

  @Override
  public Future<Integer> updateBatchBudgetsBySql(String sql, DBConn conn) {
    logger.debug("Trying update batch budgets by query: {}", sql);
    return conn.execute(sql)
      .map(SqlResult::rowCount)
      .onSuccess(rowCount -> logger.info("updateBatchBudgetsBySql:: Updated {} batch budgets", rowCount))
      .onFailure(e -> logger.error("Update batch budgets by query: {} failed", sql, e));
  }

  @Override
  public Future<List<Budget>> getBudgetsBySql(String sql, Tuple params, DBConn conn) {
    logger.debug("Trying to get budgets by sql: {}", sql);
    return conn.execute(sql, params)
      .map(rowSet -> {
        List<Budget> budgets = new ArrayList<>();
        rowSet.spliterator().forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(Budget.class)));
        budgets.forEach(CalculationUtils::calculateBudgetSummaryFields);
        return budgets;
      })
      .onSuccess(budgets -> logger.info("Successfully retrieved {} budgets in transactional by sql", budgets.size()))
      .onFailure(e -> logger.error("Getting budgets in transactional by sql failed", e));
  }

  /**
   * Returns a list of budgets by criteria.
   *
   * @param criterion the set of filters for searching budgets
   * @param conn : db connection
   */
  @Override
  public Future<List<Budget>> getBudgetsByCriterion(Criterion criterion, DBConn conn) {
    logger.debug("Trying to get budgets by query: {}", criterion);
    return conn.get(BUDGET_TABLE, Budget.class, criterion, false)
      .map(results -> {
        List<Budget> budgets = results.getResults();
        budgets.forEach(CalculationUtils::calculateBudgetSummaryFields);
        return budgets;
      })
      .onSuccess(budgets -> logger.info("Successfully retrieved {} budgets by query: {}", budgets.size(), criterion))
      .onFailure(e -> logger.error("Getting budgets by query: {} failed", criterion, e));
  }

  public Future<Budget> getBudgetById(String id, DBConn conn) {
    logger.debug("Trying to get a budget by id {}", id);
    return conn.getById(BUDGET_TABLE, id, Budget.class)
      .map(budget -> {
        if (budget == null) {
          logger.warn("Budget with id {} not found", id);
          throw new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase());
        } else {
          CalculationUtils.calculateBudgetSummaryFields(budget);
          return budget;
        }
      })
      .onSuccess(budgets -> logger.info("Successfully retrieved a budget with id {}", id))
      .onFailure(e -> logger.error("Getting a budget by id {} failed", id, e));
  }

  public Future<Void> deleteBudget(String id, DBConn conn) {
    logger.debug("Trying to delete a budget by id {}", id);
    return conn.delete(BUDGET_TABLE, id)
      .recover(t -> {
        if (checkForeignKeyViolationError(t.getCause())) {
          logger.error("Can't delete budget with id {} that referenced with expense class", id, t);
          return Future.failedFuture(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(),
            buildErrorForBudgetExpenseClassReferenceError()));
        }
        return Future.failedFuture(t);
      })
      .onSuccess(v -> logger.info("Successfully deleted a budget with id {}", id))
      .onFailure(e -> logger.error("Deleting a budget by id {} failed", id, e))
      .mapEmpty();
  }
  private boolean checkForeignKeyViolationError(Throwable cause) {
    if (cause != null && PgExceptionUtil.isForeignKeyViolation(cause)) {
      Map<Character,String> errorMessageMap = PgExceptionUtil.getBadRequestFields(cause);
      String details = errorMessageMap.getOrDefault('D', "");
      return details.contains("budget_expense_class");
    }
    return false;
  }

  private String buildErrorForBudgetExpenseClassReferenceError() {
    return JsonObject.mapFrom(BUDGET_EXPENSE_CLASS_REFERENCE_ERROR.toError()).encodePrettily();
  }

}
