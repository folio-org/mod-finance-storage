package org.folio.service.fund;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.fund.FundDAO;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.DBConn;

import java.util.List;

public class StorageFundService implements FundService {
  private static final Logger logger = LogManager.getLogger();

  private final FundDAO fundDAO;

  public StorageFundService(FundDAO fundDAO) {
    this.fundDAO = fundDAO;
  }

  @Override
  public Future<Fund> getFundById(String fundId, DBConn conn) {
    return fundDAO.getFundById(fundId, conn);
  }

  @Override
  public Future<List<Fund>> getFundsByIds(List<String> ids, DBConn conn) {
    return fundDAO.getFundsByIds(ids, conn);
  }

  @Override
  public Future<Void> updateFund(Fund fund, DBConn conn) {
    return fundDAO.isFundStatusChanged(fund, conn)
      .compose(statusChanged -> {
        if (Boolean.TRUE.equals(statusChanged)) {
          return fundDAO.updateRelatedCurrentFYBudgets(fund, conn)
            .compose(v -> fundDAO.updateFundById(fund.getId(), fund, conn));
        }
        return updateFundWithMinChange(fund, conn);
      });
  }

  @Override
  public Future<Void> updateFundWithMinChange(Fund fund, DBConn conn) {
    return fundDAO.updateFundById(fund.getId(), fund, conn);
  }
}
