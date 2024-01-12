package org.folio.dao.rollover;

import static org.folio.rest.impl.LedgerRolloverProgressAPI.LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE;

import io.vertx.core.Future;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.interfaces.Results;

public class RolloverProgressDAO {

  private static final Logger logger = LogManager.getLogger(RolloverProgressDAO.class);

  public Future<List<LedgerFiscalYearRolloverProgress>> get(Criterion filter, DBConn conn) {
    logger.debug("Trying to get rollover progress by query: {}", filter);
    return conn.get(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, LedgerFiscalYearRolloverProgress.class, filter, true)
      .map(Results::getResults)
      .onSuccess(list -> logger.info("Successfully retrieved {} rollover progress by query: {}", list.size(), filter))
      .onFailure(e -> logger.error("Getting rollover progress by query: {} failed", filter, e));
  }

  public Future<Void> create(LedgerFiscalYearRolloverProgress progress, DBConn conn) {
    logger.debug("Trying to create rollover progress with id {}", progress.getId());
    return conn.save(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, progress.getId(), progress)
      .onSuccess(s -> logger.info("Successfully created a rollover progress with id {}", progress.getId()))
      .onFailure(e -> logger.error("Creating rollover progress with id {} failed", progress.getId(), e))
      .mapEmpty();
  }

  public Future<Void> update(LedgerFiscalYearRolloverProgress progress, DBConn conn) {
    logger.debug("Trying to update rollover progress with id {}", progress.getId());
    return conn.update(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, progress, progress.getId())
      .onSuccess(s -> logger.info("Successfully updated a rollover progress with id {}", progress.getId()))
      .onFailure(e -> logger.error("Updating rollover progress with id {} failed", progress.getId(), e))
      .mapEmpty();
  }

  public Future<Void> delete(String id, DBConn conn) {
    logger.debug("Trying to delete rollover progress by id {}", id);
    return conn.delete(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, id)
      .onSuccess(s -> logger.info("Successfully deleted a rollover progress with id {}", id))
      .onFailure(e -> logger.error("Deleting rollover progress by id {} failed", id, e))
      .mapEmpty();
  }
}
