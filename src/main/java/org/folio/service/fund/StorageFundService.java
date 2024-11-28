package org.folio.service.fund;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.fund.FundDAO;
import org.folio.rest.core.model.RequestContext;
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
  public Future<Void> updateFund(Fund fund, RequestContext requestContext) {
    var dbClient = requestContext.toDBClient();
    return dbClient.withTrans(conn ->
      fundDAO.isFundStatusChanged(fund, conn)
        .compose(statusChanged -> {
          if (Boolean.TRUE.equals(statusChanged)) {
            return fundDAO.updateRelatedCurrentFYBudgets(fund, conn)
              .compose(v -> fundDAO.updateFund(fund, conn));
          }
          return fundDAO.updateFund(fund, conn);
        })
    );
  }

  @Override
  public Future<Void> updateFundsWithMinChange(List<Fund> funds, DBConn conn) {
    return fundDAO.updateFunds(funds, conn);
  }
}
