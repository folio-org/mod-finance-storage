package org.folio.dao.budget;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.folio.rest.jaxrs.model.BudgetExpenseClass;
import org.folio.rest.persist.DBClient;

import java.util.ArrayList;
import java.util.List;

import static org.folio.rest.util.ResponseUtils.handleFailure;

public class BudgetExpenseClassDAOImpl implements BudgetExpenseClassDAO{
  public static final String TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE = "tmp_budget_expense_class";

  private static final String SELECT_BUDGET_EXPENSE_CLASSES_BY_BUDGET_ID = "SELECT budget_expense_class.jsonb "
    + "FROM %s AS budget_expense_class "
    + "WHERE budget_expense_class.budgetid = $1";

  public Future<List<BudgetExpenseClass>> getTemporaryBudgetExpenseClasses(String budgetId, DBClient client) {
    Promise<List<BudgetExpenseClass>> promise = Promise.promise();
    client.getPgClient()
      .select(getSelectExpenseClassQueryByBudgetId(), Tuple.of(budgetId), reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<BudgetExpenseClass> budgets = new ArrayList<>();
          reply.result().spliterator()
            .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(BudgetExpenseClass.class)));
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  private String getSelectExpenseClassQueryByBudgetId(){
    return String.format(SELECT_BUDGET_EXPENSE_CLASSES_BY_BUDGET_ID, TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE);
  }

}
