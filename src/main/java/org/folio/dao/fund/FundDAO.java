package org.folio.dao.fund;

import java.util.List;

import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;

public interface FundDAO {

  Future<List<Fund>> getFunds(Criterion criterion, DBConn conn);
}
