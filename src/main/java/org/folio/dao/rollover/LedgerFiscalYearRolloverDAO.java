package org.folio.dao.rollover;

import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class LedgerFiscalYearRolloverDAO {

  private static final Logger logger = LogManager.getLogger(LedgerFiscalYearRolloverDAO.class);

  public static final String LEDGER_FISCAL_YEAR_ROLLOVER_TABLE = "ledger_fiscal_year_rollover";

  public Future<Void> create(LedgerFiscalYearRollover rollover, DBClient client) {
    logger.debug("Trying to create ledger fiscal year rollover");
    if (StringUtils.isEmpty(rollover.getId())) {
      rollover.setId(UUID.randomUUID()
        .toString());
    }
    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .save(client.getConnection(), LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, rollover.getId(), rollover, reply -> {
        if (reply.failed()) {
          logger.error("Creating ledger fiscal year rollover with id {} failed", rollover.getId(), reply.cause());
          handleFailure(promise, reply);
        } else {
          logger.info("Successfully created a ledger fiscal year rollover with id {}", rollover.getId());
          promise.complete();
        }
      });
    return promise.future();
  }

  public Future<Void> delete(String id, DBClient client) {
    logger.debug("Trying to delete ledger fiscal year rollover by id {}", id);
    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .delete(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, id, reply -> {
        if (reply.failed()) {
          logger.error("Deleting ledger fiscal year rollover by id {} failed", id, reply.cause());
          handleFailure(promise, reply);
        } else {
          logger.info("Successfully deleted a ledger fiscal year rollover by id {}", id);
          promise.complete();
        }
      });
    return promise.future();
  }

}
