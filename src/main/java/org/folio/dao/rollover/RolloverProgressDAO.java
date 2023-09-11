package org.folio.dao.rollover;

import static org.folio.rest.impl.LedgerRolloverProgressAPI.LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;

public class RolloverProgressDAO {

  private static final Logger logger = LogManager.getLogger(RolloverProgressDAO.class);

  public Future<List<LedgerFiscalYearRolloverProgress>> get(Criterion filter, DBClient client) {
    logger.debug("Trying to get rollover progress by query: {}", filter);
    Promise<List<LedgerFiscalYearRolloverProgress>> promise = Promise.promise();
    client.getPgClient()
      .get(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, LedgerFiscalYearRolloverProgress.class, filter, true, reply -> {
        if (reply.failed()) {
          logger.error("Getting rollover progress by query: {} failed", filter, reply.cause());
          handleFailure(promise, reply);
        } else {
          logger.info("Successfully retrieved {} rollover progress by query: {}", reply.result().getResults().size(), filter);
          promise.complete(reply.result().getResults());
        }
      });

    return promise.future();
  }

  public Future<Void> create(LedgerFiscalYearRolloverProgress progress, DBClient client) {
    logger.debug("Trying to create rollover progress with id {}", progress.getId());
    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .save(client.getConnection(), LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, progress.getId(), progress, reply -> {
        if (reply.failed()) {
          logger.error("Creating rollover progress with id {} failed", progress.getId(), reply.cause());
          handleFailure(promise, reply);
        } else {
          logger.info("Successfully created a rollover progress with id {}", progress.getId());
          promise.complete();
        }
      });

    return promise.future();
  }

  public Future<Void> update(LedgerFiscalYearRolloverProgress progress, DBClient client) {
    logger.debug("Trying to update rollover progress with id {}", progress.getId());
    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .update(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, progress, progress.getId(), reply -> {
        if (reply.failed()) {
          logger.error("Updating rollover progress with id {} failed", progress.getId(), reply.cause());
          handleFailure(promise, reply);
        } else {
          logger.info("Successfully updated a rollover progress with id {}", progress.getId());
          promise.complete();
        }
      });
    return promise.future();
  }

  public Future<Void> delete(String id, DBClient client) {
    logger.debug("Trying to delete rollover progress by id {}", id);
    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .delete(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, id, reply -> {
        if (reply.failed()) {
          logger.error("Deleting rollover progress by id {} failed", id, reply.cause());
          handleFailure(promise, reply);
        } else {
          logger.info("Successfully deleted a rollover progress with id {}", id);
          promise.complete();
        }
      });
    return promise.future();
  }
}
