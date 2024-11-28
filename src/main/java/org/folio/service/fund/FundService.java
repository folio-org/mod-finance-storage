package org.folio.service.fund;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.DBConn;

import java.util.List;

public interface FundService {
  Future<Fund> getFundById(String fundId, DBConn conn);
  Future<List<Fund>> getFundsByIds(List<String> ids, DBConn conn);
  Future<Void> updateFund(Fund fund, DBConn conn);
  Future<Void> updateFundsWithMinChange(List<Fund> fund, DBConn conn);
}
