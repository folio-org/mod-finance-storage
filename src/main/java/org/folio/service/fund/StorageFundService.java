package org.folio.service.fund;

import io.vertx.core.Future;
import org.folio.dao.fund.FundDAO;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.DBConn;

import java.util.List;

public class StorageFundService implements FundService {

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
  public Future<Void> updateFundById(String fundId, Fund fund, DBConn conn) {
    return fundDAO.updateFundById(fundId, fund, conn);
  }
}
