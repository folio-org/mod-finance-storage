package org.folio.service.fund;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.DBClient;

import java.util.List;

public interface FundService {

  Future<List<Fund>> getFundsByBudgets(List<Budget> budgets, DBClient client);

  Future<Fund> getFundById(String fundId, DBClient dbClient);
}
