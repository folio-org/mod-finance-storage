package org.folio.dao.fiscalyear;

import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.HelperUtils.getRowSetAsList;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import javax.ws.rs.core.Response;

import org.folio.rest.exception.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.persist.DBConn;

public class FiscalYearPostgresDAO implements FiscalYearDAO {

  private static final Logger logger = LogManager.getLogger(FiscalYearPostgresDAO.class);

  public static final String FISCAL_YEAR_HIERARCHY_VIEW = "fiscal_year_hierarchy_view";

  private static final String HIERARCHY_ROWS_QUERY =
    "SELECT jsonb FROM %s WHERE (jsonb->>'fiscalYearId') = $1";

  private static final String FISCAL_YEAR_NOT_FOUND = "Fiscal year not found by id=%s";

  public Future<FiscalYear> getFiscalYearById(String id, DBConn conn) {
    logger.debug("Trying to get fiscal year by id {}", id);
    return conn.getById(FISCAL_YEAR_TABLE, id, FiscalYear.class)
      .map(fiscalYear -> {
        if (fiscalYear == null) {
          logger.warn(String.format(FISCAL_YEAR_NOT_FOUND, id));
          throw new HttpException(Response.Status.BAD_REQUEST.getStatusCode(), String.format(FISCAL_YEAR_NOT_FOUND, id));
        }
        return fiscalYear;
      })
      .onSuccess(fiscalYear -> logger.info("Successfully retrieved a fiscal year by id {}", id))
      .onFailure(e -> logger.error("Getting fiscal year by id {} failed", id, e));
  }

  @Override
  public Future<List<FiscalYearHierarchyFlatRow>> getFiscalYearHierarchyRows(String fiscalYearId, DBConn conn) {
    var table = getFullTableName(conn.getTenantId(), FISCAL_YEAR_HIERARCHY_VIEW);
    var sql = HIERARCHY_ROWS_QUERY.formatted(table);
    return conn.execute(sql, Tuple.of(fiscalYearId))
      .map(rows -> getRowSetAsList(rows, FiscalYearHierarchyFlatRow.class))
      .onSuccess(list -> logger.debug("Loaded {} fiscal year hierarchy row(s) for fy {}", list.size(), fiscalYearId))
      .onFailure(e -> logger.error("Loading fiscal year hierarchy rows for fy {} failed", fiscalYearId, e));
  }
}
