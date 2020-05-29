package org.folio.dao.budget;

import java.util.List;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;

public interface BudgetDAO {

  Future<Integer> updateBatchBudgets(String sql, DBClient client);

  Future<List<Budget>> getBudgets(String sql, JsonArray params, DBClient client);

  Future<List<Budget>> getBudgets(Criterion criterion, DBClient client);

  Future<Budget> getBudgetById(String id, DBClient client);

  Future<DBClient> deleteBudget(String id, DBClient client);
}
