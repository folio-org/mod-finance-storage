package org.folio.dao.rollover;

import java.util.List;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import static org.folio.rest.util.ResponseUtils.handleFailure;

public class RolloverErrorDAO {

  public static final String LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE = "ledger_fiscal_year_rollover_error";

  public Future<List<LedgerFiscalYearRolloverError>> get(Criterion filter, DBClient client) {
    Promise<List<LedgerFiscalYearRolloverError>> promise = Promise.promise();
    client.getPgClient()
      .get(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, LedgerFiscalYearRolloverError.class, filter, true, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(reply.result().getResults());
        }
      });
    return promise.future();
  }

  public Future<Void> create(LedgerFiscalYearRolloverError rolloverError, DBClient client) {
    return client.save(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, rolloverError.getId(), rolloverError);
  }

  public Future<Void> deleteByQuery(Criterion filter, DBClient client) {
    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .delete(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, filter, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete();
        }
      });
    return promise.future();
  }
}
