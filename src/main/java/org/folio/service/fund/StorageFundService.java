package org.folio.service.fund;

import io.vertx.core.Future;
import io.vertx.ext.web.handler.HttpException;
import org.folio.dao.fund.FundDAO;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;

public class StorageFundService implements FundService {

  private static final String FUND_NOT_FOUND_FOR_TRANSACTION = "Fund not found for transaction";

  private final FundDAO fundDAO;

  public StorageFundService(FundDAO fundDAO) {
    this.fundDAO = fundDAO;
  }

  @Override
  public Future<Fund> getFundById(String fundId, DBConn conn) {
    CriterionBuilder criterionBuilder = new CriterionBuilder();
    criterionBuilder.with("id", fundId);

    return fundDAO.getFunds(criterionBuilder.build(), conn)
      .map(funds -> {
        if (funds.isEmpty()) {
          throw new HttpException(404, FUND_NOT_FOUND_FOR_TRANSACTION);
        }
        return funds.get(0);
    });
  }
}
