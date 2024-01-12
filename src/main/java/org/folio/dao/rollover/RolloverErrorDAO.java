package org.folio.dao.rollover;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.persist.Criteria.Criterion;
import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.interfaces.Results;

public class RolloverErrorDAO {

  private static final Logger logger = LogManager.getLogger(RolloverErrorDAO.class);

  public static final String LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE = "ledger_fiscal_year_rollover_error";

  public Future<List<LedgerFiscalYearRolloverError>> get(Criterion filter, DBConn conn) {
    logger.debug("Trying to get rollover error by query: {}", filter);
    return conn.get(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, LedgerFiscalYearRolloverError.class, filter, true)
      .map(Results::getResults)
      .onSuccess(list -> logger.info("Successfully retrieved {} rollover errors by query: {}", list.size(), filter))
      .onFailure(e -> logger.error("Getting rollover errors by query: {} failed", filter, e));
  }

  public Future<Void> create(LedgerFiscalYearRolloverError rolloverError, DBConn conn) {
    logger.debug("Trying to create rollover error with id {}", rolloverError.getId());
    return conn.save(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, rolloverError.getId(), rolloverError)
      .mapEmpty();
  }

  public Future<Void> deleteByQuery(Criterion filter, DBConn conn) {
    logger.debug("Trying to delete rollover errors by query: {}", filter);
    return conn.delete(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, filter)
      .onSuccess(list -> logger.info("Successfully deleted rollover budgets by query: {}", filter))
      .onFailure(e -> logger.error("Deleting rollover errors by query: {} failed", filter, e))
      .mapEmpty();
  }
}
