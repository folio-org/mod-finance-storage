package org.folio.dao.fiscalyear;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.persist.DBClient;

public interface FiscalYearDAO {
  Future<FiscalYear> getFiscalYearById(String id, DBClient client);
}
