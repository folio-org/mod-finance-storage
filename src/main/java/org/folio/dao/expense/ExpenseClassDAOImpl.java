package org.folio.dao.expense;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.persist.DBClient;

import java.util.ArrayList;
import java.util.List;

import static org.folio.dao.budget.BudgetExpenseClassDAOImpl.TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;

public class ExpenseClassDAOImpl implements ExpenseClassDAO {
  private static final String EXPENSE_CLASS_TABLE = "expense_class";

  public static final String SELECT_EXPENSE_CLASSES_BY_BUDGET_ID = "SELECT expense_class.jsonb FROM %s AS expense_class "
    + "INNER JOIN %s AS budget_expense_class ON expense_class.id::text = budget_expense_class.jsonb->>'expenseClassId' "
    + "WHERE budget_expense_class.jsonb->>'budgetId' = $1";

  public Future<List<ExpenseClass>> getExpenseClassesByTemporaryBudgetId(String budgetId, DBClient client) {
    Promise<List<ExpenseClass>> promise = Promise.promise();
    client.getPgClient()
      .select(getSelectExpenseClassQueryByBudgetId(client.getTenantId()), Tuple.of(budgetId), reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<ExpenseClass> budgets = new ArrayList<>();
          reply.result().spliterator()
            .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0).mapTo(ExpenseClass.class)));
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  private String getSelectExpenseClassQueryByBudgetId(String tenantId){
    String budgetTableName = getFullTableName(tenantId, EXPENSE_CLASS_TABLE);
    return String.format(SELECT_EXPENSE_CLASSES_BY_BUDGET_ID, budgetTableName, TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE);
  }

}
