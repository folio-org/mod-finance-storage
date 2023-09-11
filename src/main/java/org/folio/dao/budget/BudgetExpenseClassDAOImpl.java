package org.folio.dao.budget;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.BudgetExpenseClass;
import org.folio.rest.persist.DBClient;

import java.util.ArrayList;
import java.util.List;

import static org.folio.rest.util.ResponseUtils.handleFailure;

public class BudgetExpenseClassDAOImpl implements BudgetExpenseClassDAO {

  private static final Logger logger = LogManager.getLogger(BudgetExpenseClassDAOImpl.class);

  public static final String TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE = "tmp_budget_expense_class";

  private static final String SELECT_BUDGET_EXPENSE_CLASSES_BY_BUDGET_ID = "SELECT budget_expense_class.jsonb "
    + "FROM %s AS budget_expense_class "
    + "WHERE budget_expense_class.jsonb->>'budgetId' = $1";

  public Future<List<BudgetExpenseClass>> getTemporaryBudgetExpenseClasses(String budgetId, DBClient client) {
    logger.debug("Trying to get temporary budget expense classes by budgetid {}", budgetId);
    Promise<List<BudgetExpenseClass>> promise = Promise.promise();
    client.getPgClient()
      .select(getSelectExpenseClassQueryByBudgetId(), Tuple.of(budgetId), reply -> {
        if (reply.failed()) {
          logger.error("Getting temporary budget expense classes by budgetid {} failed", budgetId, reply.cause());
          handleFailure(promise, reply);
        } else {
          List<BudgetExpenseClass> budgets = new ArrayList<>();
          reply.result().spliterator()
            .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(BudgetExpenseClass.class)));
          logger.info("Successfully retrieved {} temporary budget expense classes with budgetid {}", budgets.size(), budgetId);
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  private String getSelectExpenseClassQueryByBudgetId(){
    return String.format(SELECT_BUDGET_EXPENSE_CLASSES_BY_BUDGET_ID, TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE);
  }

}
