package org.folio.dao.expense;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.persist.DBConn;

import java.util.ArrayList;
import java.util.List;

import static org.folio.dao.budget.BudgetExpenseClassDAOImpl.TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

public class ExpenseClassDAOImpl implements ExpenseClassDAO {

  private static final Logger logger = LogManager.getLogger(ExpenseClassDAOImpl.class);

  private static final String EXPENSE_CLASS_TABLE = "expense_class";
  public static final String SELECT_EXPENSE_CLASSES_BY_BUDGET_ID = "SELECT expense_class.jsonb FROM %s AS expense_class "
    + "INNER JOIN %s AS budget_expense_class ON expense_class.id::text = budget_expense_class.jsonb->>'expenseClassId' "
    + "WHERE budget_expense_class.jsonb->>'budgetId' = $1";

  public Future<List<ExpenseClass>> getExpenseClassesByTemporaryBudgetId(String budgetId, DBConn conn) {
    logger.debug("Trying to get expense classes by temporary budgetid {}", budgetId);
    return conn.execute(getSelectExpenseClassQueryByBudgetId(conn.getTenantId()), Tuple.of(budgetId))
      .map(rowSet -> {
        List<ExpenseClass> ecList = new ArrayList<>();
        rowSet.spliterator()
          .forEachRemaining(row -> ecList.add(row.get(JsonObject.class, 0).mapTo(ExpenseClass.class)));
        return ecList;
      })
      .onSuccess(ecList -> logger.info("Successfully retrieved {} expense classes by temporary budgetid {}", ecList.size(), budgetId))
      .onFailure(e -> logger.error("Getting expense classes by temporary budgetid {} failed", budgetId, e));
  }

  private String getSelectExpenseClassQueryByBudgetId(String tenantId){
    String budgetTableName = getFullTableName(tenantId, EXPENSE_CLASS_TABLE);
    return String.format(SELECT_EXPENSE_CLASSES_BY_BUDGET_ID, budgetTableName, TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE);
  }

}
