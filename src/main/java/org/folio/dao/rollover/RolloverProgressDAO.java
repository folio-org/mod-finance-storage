package org.folio.dao.rollover;

import static org.folio.rest.impl.LedgerRolloverProgressAPI.LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class RolloverProgressDAO {

  public Future<Void> create(LedgerFiscalYearRolloverProgress progress, DBClient client) {

    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .save(client.getConnection(), LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, progress.getId(), progress, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete();
        }
      });

    return promise.future();
  }

  public Future<Void> update(LedgerFiscalYearRolloverProgress progress, DBClient client) {
    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .update(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, progress, progress.getId(), reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete();
        }
      });
    return promise.future();
  }
}
