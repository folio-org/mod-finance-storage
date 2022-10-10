package org.folio.service.budget;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.budget.BudgetExpenseClassDAO;
import org.folio.dao.expense.ExpenseClassDAO;
import org.folio.rest.jaxrs.model.BudgetExpenseClass;
import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.persist.DBClient;

import java.util.List;

import static org.folio.rest.persist.HelperUtils.getFullTableName;

public class BudgetExpenseClassService {
  private static final String EXPENSE_CLASS_TABLE = "expense_class";
  private static final String TEMP_BUDGET_EXPENSE_CLASS_TABLE = "tmp_budget_expense_class";

  public static final String SELECT_EXPENSE_CLASSES_BY_BUDGET_ID = "SELECT expense_class.jsonb FROM %s AS expense_class "
    + "INNER JOIN %s AS budget_expense_class ON expense_class.id::text = budget_expense_class.jsonb->>'expenseClassId' "
    + "WHERE budget_expense_class.jsonb->>'budgetId' = $1";
  public static final String SELECT_BUDGET_EXPENSE_CLASSES_BY_BUDGET_ID = "SELECT budget_expense_class.jsonb "
    + "FROM %s AS budget_expense_class "
    + "WHERE budget_expense_class.budgetid = $1";

  private final ExpenseClassDAO expenseClassDAO;
  private final BudgetExpenseClassDAO budgetExpenseClassDAO;

  public BudgetExpenseClassService(ExpenseClassDAO expenseClassDAO, BudgetExpenseClassDAO budgetExpenseClassDAO) {
    this.expenseClassDAO = expenseClassDAO;
    this.budgetExpenseClassDAO = budgetExpenseClassDAO;
  }

  public Future<List<BudgetExpenseClass>> getTempBudgetExpenseClasses(String budgetId, DBClient client) {
    return budgetExpenseClassDAO.getBudgetExpenseClasses(getSelectExpenseClassQueryByBudgetId(), Tuple.of(budgetId), client);
  }

  public Future<List<ExpenseClass>> getExpenseClassesByTemporaryBudgetId(String budgetId, DBClient client) {
    String sql = getSelectExpenseClassQueryByBudgetId(client.getTenantId());
    return expenseClassDAO.getExpenseClasses(sql, Tuple.of(budgetId), client);
  }

  private String getSelectExpenseClassQueryByBudgetId(){
    return String.format(SELECT_BUDGET_EXPENSE_CLASSES_BY_BUDGET_ID, TEMP_BUDGET_EXPENSE_CLASS_TABLE);
  }

  private String getSelectExpenseClassQueryByBudgetId(String tenantId){
    String budgetTableName = getFullTableName(tenantId, EXPENSE_CLASS_TABLE);
    return String.format(SELECT_EXPENSE_CLASSES_BY_BUDGET_ID, budgetTableName, TEMP_BUDGET_EXPENSE_CLASS_TABLE);
  }

}
