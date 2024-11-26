package org.folio.dao.fund;

import java.util.List;

import org.folio.rest.jaxrs.model.Fund;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;

public interface FundDAO {
  Future<Fund> getFundById(String id, DBConn conn);
  Future<List<Fund>> getFundsByIds(List<String> ids, DBConn conn);
  Future<Void> updateFundById(String fundId, Fund fund, DBConn conn);
}
