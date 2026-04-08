package org.folio.dao.fiscalyear;

import io.vertx.core.Future;
import java.util.List;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.persist.DBConn;

public interface FiscalYearDAO {
  Future<FiscalYear> getFiscalYearById(String id, DBConn conn);

  Future<List<FiscalYearHierarchyFlatRow>> getFiscalYearHierarchyRows(String fiscalYearId, DBConn conn);
}
