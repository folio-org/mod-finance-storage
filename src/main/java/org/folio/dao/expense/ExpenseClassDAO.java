package org.folio.dao.expense;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.persist.DBConn;

import java.util.List;

public interface ExpenseClassDAO {
  Future<List<ExpenseClass>> getExpenseClassesByTemporaryBudgetId(String budgetId, DBConn conn);
}
