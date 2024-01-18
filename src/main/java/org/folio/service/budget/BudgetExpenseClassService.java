package org.folio.service.budget;

import io.vertx.core.Future;
import org.folio.dao.budget.BudgetExpenseClassDAO;
import org.folio.dao.expense.ExpenseClassDAO;
import org.folio.rest.jaxrs.model.BudgetExpenseClass;
import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.persist.DBConn;

import java.util.List;

public class BudgetExpenseClassService {

  private final ExpenseClassDAO expenseClassDAO;
  private final BudgetExpenseClassDAO budgetExpenseClassDAO;

  public BudgetExpenseClassService(ExpenseClassDAO expenseClassDAO, BudgetExpenseClassDAO budgetExpenseClassDAO) {
    this.expenseClassDAO = expenseClassDAO;
    this.budgetExpenseClassDAO = budgetExpenseClassDAO;
  }

  public Future<List<BudgetExpenseClass>> getTempBudgetExpenseClasses(String budgetId, DBConn conn) {
    return budgetExpenseClassDAO.getTemporaryBudgetExpenseClasses(budgetId, conn);
  }

  public Future<List<ExpenseClass>> getExpenseClassesByTemporaryBudgetId(String budgetId, DBConn conn) {
    return expenseClassDAO.getExpenseClassesByTemporaryBudgetId(budgetId, conn);
  }

}
