package org.folio.dao.budget;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import org.folio.rest.jaxrs.model.BudgetExpenseClass;
import org.folio.rest.persist.DBClient;

import java.util.List;

public interface BudgetExpenseClassDAO {
  Future<List<BudgetExpenseClass>> getBudgetExpenseClasses(String sql, Tuple params, DBClient client);
}
