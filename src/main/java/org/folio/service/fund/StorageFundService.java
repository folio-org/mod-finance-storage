package org.folio.service.fund;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;
import org.folio.dao.fund.FundDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;

import java.util.List;

public class StorageFundService implements FundService{

  private static final String FUND_NOT_FOUND_FOR_TRANSACTION = "Fund not found for transaction";

  private FundDAO fundDAO;

  public StorageFundService(FundDAO fundDAO) {
    this.fundDAO = fundDAO;
  }

  public Future<List<Fund>> getFundsByBudgets(List<Budget> budgets, DBClient client) {
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    budgets.stream()
      .map(Budget::getFundId)
      .forEach(id -> criterionBuilder.with("id", id));
    return fundDAO.getFunds(criterionBuilder.build(), client);
  }

  @Override
  public Future<Fund> getFundById(String fundId, DBClient client) {
    CriterionBuilder criterionBuilder = new CriterionBuilder();
    criterionBuilder.with("id", fundId);

    return fundDAO.getFunds(criterionBuilder.build(), client)
      .map(funds -> {
        if (funds.isEmpty()) {
          throw new HttpException(404, FUND_NOT_FOUND_FOR_TRANSACTION);
        }
        return funds.get(0);
    });
  }
}
