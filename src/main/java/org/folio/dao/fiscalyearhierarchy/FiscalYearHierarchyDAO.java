package org.folio.dao.fiscalyearhierarchy;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.FiscalYearHierarchyCollection;
import org.folio.rest.persist.DBConn;

public interface FiscalYearHierarchyDAO {

  Future<FiscalYearHierarchyCollection> getFiscalYearHierarchy(DBConn conn, String fiscalYearId);

}
