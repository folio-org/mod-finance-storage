package org.folio.dao.rollover;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import static org.folio.rest.util.ResponseUtils.handleFailure;

public class RolloverErrorDAO {

  private static final Logger logger = LogManager.getLogger(RolloverErrorDAO.class);

  public static final String LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE = "ledger_fiscal_year_rollover_error";

  public Future<List<LedgerFiscalYearRolloverError>> get(Criterion filter, DBClient client) {
    logger.debug("Trying to get rollover error by query: {}", filter);
    Promise<List<LedgerFiscalYearRolloverError>> promise = Promise.promise();
    client.getPgClient()
      .get(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, LedgerFiscalYearRolloverError.class, filter, true, reply -> {
        if (reply.failed()) {
          logger.error("Getting rollover errors by query: {} failed", filter, reply.cause());
          handleFailure(promise, reply);
        } else {
          logger.info("Successfully retrieved {} rollover errors by query: {}", reply.result().getResults().size(), filter);
          promise.complete(reply.result().getResults());
        }
      });
    return promise.future();
  }

  public Future<Void> create(LedgerFiscalYearRolloverError rolloverError, DBClient client) {
    logger.debug("Trying to create rollover error with id {}", rolloverError.getId());
    return client.save(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, rolloverError.getId(), rolloverError);
  }

  public Future<Void> deleteByQuery(Criterion filter, DBClient client) {
    logger.debug("Trying to delete rollover errors by query: {}", filter);
    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .delete(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, filter, reply -> {
        if (reply.failed()) {
          logger.error("Deleting rollover errors by query: {} failed", filter, reply.cause());
          handleFailure(promise, reply);
        } else {
          logger.info("Successfully deleted rollover budgets by query: {}", filter);
          promise.complete();
        }
      });
    return promise.future();
  }
}
