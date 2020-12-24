package org.folio.dao.budget;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.util.ErrorCodes.BUDGET_EXPENSE_CLASS_REFERENCE_ERROR;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.ArrayList;
import java.util.List;

import java.util.Map;
import javax.ws.rs.core.Response;

import io.vertx.sqlclient.Tuple;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.persist.PgExceptionUtil;
import org.folio.utils.CalculationUtils;

public class BudgetPostgresDAO implements BudgetDAO {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  public Future<Integer> updateBatchBudgets(String sql, DBClient client) {
    Promise<Integer> promise = Promise.promise();

    client.getPgClient().execute(client.getConnection(), sql, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        promise.complete(reply.result().rowCount());
      }
    });
    return promise.future();
  }

  public Future<List<Budget>> getBudgets(String sql, Tuple params, DBClient client) {
    Promise<List<Budget>> promise = Promise.promise();
    client.getPgClient()
      .select(client.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Budget> budgets = new ArrayList<>();
          reply.result().spliterator()
            .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(Budget.class)));
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  public Future<List<Budget>> getBudgets(Criterion criterion, DBClient client) {
    Promise<List<Budget>> promise = Promise.promise();
    client.getPgClient().get(BUDGET_TABLE, Budget.class, criterion, false, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Budget> budgets = reply.result()
            .getResults();
          budgets.forEach(CalculationUtils::calculateBudgetSummaryFields);
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  public Future<Budget> getBudgetById(String id, DBClient client) {
    Promise<Budget> promise = Promise.promise();

    logger.debug("Get budget={}", id);

    client.getPgClient().getById(BUDGET_TABLE, id, reply -> {
      if (reply.failed()) {
        logger.error("Budget retrieval with id={} failed", reply.cause(), id);
        handleFailure(promise, reply);
      } else {
        final JsonObject budget = reply.result();
        if (budget == null) {
          promise.fail(new HttpStatusException(Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND.getReasonPhrase()));
        } else {
          logger.debug("Budget with id={} successfully extracted", id);
          Budget convertedBudget = budget.mapTo(Budget.class);
          CalculationUtils.calculateBudgetSummaryFields(convertedBudget);
          promise.complete(convertedBudget);
        }
      }
    });
    return promise.future();
  }

  public Future<DBClient> deleteBudget(String id, DBClient client) {
    Promise<DBClient> promise = Promise.promise();
    client.getPgClient().delete(client.getConnection(), BUDGET_TABLE, id, reply -> {
      if (reply.failed()) {
        logger.error("Budget deletion with id={} failed", reply.cause(), id);
        if (checkForeignKeyViolationError(reply.cause())) {
          promise.fail(new HttpStatusException(Response.Status.BAD_REQUEST.getStatusCode(),
            buildErrorForBudgetExpenseClassReferenceError()));
        } else {
          handleFailure(promise, reply);
        }
      } else if (reply.result().rowCount() == 0) {
        promise.fail(new HttpStatusException(NOT_FOUND.getStatusCode(), NOT_FOUND.getReasonPhrase()));
      } else {
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
