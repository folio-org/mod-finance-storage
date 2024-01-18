package org.folio.dao.fiscalyear;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.persist.DBConn;

public interface FiscalYearDAO {
  Future<FiscalYear> getFiscalYearById(String id, DBConn conn);
}
