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
}
