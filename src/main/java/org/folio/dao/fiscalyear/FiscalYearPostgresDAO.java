package org.folio.dao.fiscalyear;

import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;

import io.vertx.core.Future;
import javax.ws.rs.core.Response;

import org.folio.rest.exception.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.persist.DBConn;

public class FiscalYearPostgresDAO implements FiscalYearDAO {

  private static final Logger logger = LogManager.getLogger(FiscalYearPostgresDAO.class);

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
}
