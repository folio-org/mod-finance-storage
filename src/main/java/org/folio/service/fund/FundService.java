package org.folio.service.fund;

import java.util.List;
import java.util.Map;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Context;
import io.vertx.core.Future;

public interface FundService {

  Future<List<Fund>> getFundsByBudgets(List<Budget> budgets, DBClient client);

  Future<Fund> getFundById(String fundId, DBClient dbClient);
}
