package org.folio.dao.budget;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.BudgetExpenseClass;
import org.folio.rest.persist.DBConn;

import java.util.ArrayList;
import java.util.List;

public class BudgetExpenseClassDAOImpl implements BudgetExpenseClassDAO {

  private static final Logger logger = LogManager.getLogger(BudgetExpenseClassDAOImpl.class);

  public static final String TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE = "tmp_budget_expense_class";

  private static final String SELECT_BUDGET_EXPENSE_CLASSES_BY_BUDGET_ID = "SELECT budget_expense_class.jsonb "
    + "FROM %s AS budget_expense_class "
    + "WHERE budget_expense_class.jsonb->>'budgetId' = $1";

  public Future<List<BudgetExpenseClass>> getTemporaryBudgetExpenseClasses(String budgetId, DBConn conn) {
    logger.debug("Trying to get temporary budget expense classes by budgetid {}", budgetId);
    return conn.execute(getSelectExpenseClassQueryByBudgetId(), Tuple.of(budgetId))
      .map(rowSet -> {
        List<BudgetExpenseClass> becList = new ArrayList<>();
        rowSet.spliterator().forEachRemaining(row ->
          becList.add(row.get(JsonObject.class, 0).mapTo(BudgetExpenseClass.class)));
        return becList;
      })
      .onSuccess(becList -> logger.info("Successfully retrieved {} temporary budget expense classes with budgetId {}",
        becList.size(), budgetId))
      .onFailure(e -> logger.error("Getting temporary budget expense classes by budgetId {} failed", budgetId, e));
  }

  private String getSelectExpenseClassQueryByBudgetId(){
    return String.format(SELECT_BUDGET_EXPENSE_CLASSES_BY_BUDGET_ID, TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE);
  }

}
