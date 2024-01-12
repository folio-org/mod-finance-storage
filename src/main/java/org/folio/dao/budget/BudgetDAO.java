package org.folio.dao.budget;

import java.util.List;

import io.vertx.sqlclient.Tuple;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;

public interface BudgetDAO {

  Future<Integer> updateBatchBudgets(String sql, DBConn conn);

  Future<List<Budget>> getBudgetsTx(String sql, Tuple params, DBConn conn);

  Future<List<Budget>> getBudgets(Criterion criterion, DBConn conn);

  Future<Budget> getBudgetById(String id, DBConn conn);

  Future<Void> deleteBudget(String id, DBConn conn);
}
