package org.folio.service.fund;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.DBConn;

public interface FundService {

  Future<Fund> getFundById(String fundId, DBConn conn);
}
