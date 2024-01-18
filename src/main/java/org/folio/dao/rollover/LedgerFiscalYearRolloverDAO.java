package org.folio.dao.rollover;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;

public class LedgerFiscalYearRolloverDAO {

  private static final Logger logger = LogManager.getLogger(LedgerFiscalYearRolloverDAO.class);

  public static final String LEDGER_FISCAL_YEAR_ROLLOVER_TABLE = "ledger_fiscal_year_rollover";

  public Future<Void> create(LedgerFiscalYearRollover rollover, DBConn conn) {
    logger.debug("Trying to create ledger fiscal year rollover");
    if (StringUtils.isEmpty(rollover.getId())) {
      rollover.setId(UUID.randomUUID().toString());
    }
    return conn.save(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, rollover.getId(), rollover)
      .onSuccess(s -> logger.info("Successfully created a ledger fiscal year rollover with id {}", rollover.getId()))
      .onFailure(e -> logger.error("Creating ledger fiscal year rollover with id {} failed", rollover.getId(), e))
      .mapEmpty();
  }

  public Future<Void> delete(String id, DBConn conn) {
    logger.debug("Trying to delete ledger fiscal year rollover by id {}", id);
    return conn.delete(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, id)
      .onSuccess(s -> logger.info("Successfully deleted a ledger fiscal year rollover by id {}", id))
      .onFailure(e -> logger.error("Deleting ledger fiscal year rollover by id {} failed", id, e))
      .mapEmpty();
  }

}
