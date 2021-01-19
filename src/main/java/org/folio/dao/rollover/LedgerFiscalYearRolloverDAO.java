package org.folio.dao.rollover;

import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class LedgerFiscalYearRolloverDAO {

    public static final String LEDGER_FISCAL_YEAR_ROLLOVER_TABLE = "ledger_fiscal_year_rollover";


    public Future<Void> create(LedgerFiscalYearRollover rollover, DBClient client) {
        if (StringUtils.isEmpty(rollover.getId())) {
            rollover.setId(UUID.randomUUID()
                    .toString());
        }
        Promise<Void> promise = Promise.promise();
        client.getPgClient()
                .save(client.getConnection(), LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, rollover.getId(), rollover, reply -> {
                    if (reply.failed()) {
                        handleFailure(promise, reply);
                    } else {
                        promise.complete();
                    }
                });
        return promise.future();
    }

  public Future<Void> delete(String id, DBClient client) {
    Promise<Void> promise = Promise.promise();
    client.getPgClient()
      .delete(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, id, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete();
        }
      });
    return promise.future();
  }

}
