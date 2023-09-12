package org.folio.dao.budget;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.util.ErrorCodes.BUDGET_EXPENSE_CLASS_REFERENCE_ERROR;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.utils.CalculationUtils;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.sqlclient.Tuple;

public class BudgetPostgresDAO implements BudgetDAO {

  private static final Logger logger = LogManager.getLogger(BudgetPostgresDAO.class);

  @Override
  public Future<Integer> updateBatchBudgets(String sql, DBClient client) {
    logger.debug("Trying update batch budgets by query: {}", sql);
    Promise<Integer> promise = Promise.promise();
    client.getPgClient().execute(client.getConnection(), sql, reply -> {
      if (reply.failed()) {
        logger.error("Update batch budgets by query: {} failed", sql, reply.cause());
        handleFailure(promise, reply);
      } else {
        logger.info("Updated {} batch budgets", reply.result().rowCount());
        promise.complete(reply.result().rowCount());
      }
    });
    return promise.future();
  }

  @Override
  public Future<List<Budget>> getBudgetsTx(String sql, Tuple params, DBClient client) {
    logger.debug("Trying to get budgets in transactional by sql");
    Promise<List<Budget>> promise = Promise.promise();
    client.getPgClient()
      .select(client.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          logger.error("Getting budgets in transactional by sql failed", reply.cause());
          handleFailure(promise, reply);
        } else {
          List<Budget> budgets = new ArrayList<>();
          reply.result().spliterator()
            .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(Budget.class)));
          logger.info("Successfully retrieved {} budgets in transactional by sql", reply.result().rowCount());
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  /**
   * Receives a list of budgets by criteria. In current implementation postgres client will create a new database connection with
   * new transaction every time.
   * Use this method if transactional mode is not required.
   *
   * @param criterion the set of filters for searching budgets
   * @param client : the list of payments and credits
   */
  @Override
  public Future<List<Budget>> getBudgets(Criterion criterion, DBClient client) {
    logger.debug("Trying to get budgets by query: {}", criterion);
    Promise<List<Budget>> promise = Promise.promise();
    client.getPgClient().get(BUDGET_TABLE, Budget.class, criterion, false, reply -> {
        if (reply.failed()) {
          logger.error("Getting budgets by query: {} failed", criterion, reply.cause());
          handleFailure(promise, reply);
        } else {
          List<Budget> budgets = reply.result()
            .getResults();
          budgets.forEach(CalculationUtils::calculateBudgetSummaryFields);
          logger.info("Successfully retrieved {} budgets by query: {}", budgets.size(), criterion);
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  /**
   * Receives a list of budgets by criteria with using the same connection. Such behavior supports processing the set
   * of operations in scope of the same transaction. This method should be used only for transactional mode.
   *
   * @param criterion the set of filters for searching budgets
   * @param client : the list of payments and credits
   */
  @Override
  public Future<List<Budget>> getBudgetsTx(Criterion criterion, DBClient client) {
    logger.debug("Trying to get budgets in transactional by query: {}", criterion);
    Promise<List<Budget>> promise = Promise.promise();
    client.getPgClient().get(client.getConnection(), BUDGET_TABLE, Budget.class, criterion, false, false, reply -> {
      if (reply.failed()) {
        logger.error("Getting budgets in transactional by query: {} failed", criterion, reply.cause());
        handleFailure(promise, reply);
      } else {
        List<Budget> budgets = reply.result().getResults();
        budgets.forEach(CalculationUtils::calculateBudgetSummaryFields);
        logger.info("Successfully retrieved {} budgets in transactional by query: {}", budgets.size(), criterion);
        promise.complete(budgets);
      }
    });
    return promise.future();
  }

  public Future<Budget> getBudgetById(String id, DBClient client) {
    logger.debug("Trying to get a budget by id {}", id);
    Promise<Budget> promise = Promise.promise();
    client.getPgClient().getById(BUDGET_TABLE, id, reply -> {
      if (reply.failed()) {
        logger.error("Getting a budget by id {} failed", id, reply.cause());
        handleFailure(promise, reply);
      } else {
        final JsonObject budget = reply.result();
        if (budget == null) {
          logger.warn("Budget with id {} not found", id, reply.cause());
          promise.fail(new HttpException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          Budget convertedBudget = budget.mapTo(Budget.class);
          CalculationUtils.calculateBudgetSummaryFields(convertedBudget);
          logger.info("Successfully retrieved a budget with id {}", id);
          promise.complete(convertedBudget);
        }
      }
    });
    return promise.future();
  }

  public Future<DBClient> deleteBudget(String id, DBClient client) {
    logger.debug("Trying to delete a budget by id {}", id);
    Promise<DBClient> promise = Promise.promise();
    client.getPgClient().delete(client.getConnection(), BUDGET_TABLE, id, reply -> {
      if (reply.failed()) {
        if (checkForeignKeyViolationError(reply.cause())) {
          logger.error("Can't delete budget with id {} that referenced with expense class", id, reply.cause());
          promise.fail(new HttpException(Response.Status.BAD_REQUEST.getStatusCode(),
            buildErrorForBudgetExpenseClassReferenceError()));
        } else {
          logger.error("Deleting a budget by id {} failed", id, reply.cause());
          handleFailure(promise, reply);
        }
      } else if (reply.result().rowCount() == 0) {
        logger.warn("Budget with id {} not found for deletion", id, reply.cause());
        promise.fail(new HttpException(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase()));
      } else {
        logger.info("Successfully deleted a budget with id {}", id);
        promise.complete(client);
      }
    });
    return promise.future();
  }
  private boolean checkForeignKeyViolationError(Throwable cause) {
    if ( PgExceptionUtil.isForeignKeyViolation(cause)) {
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
