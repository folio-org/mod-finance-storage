package org.folio.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;

import static org.folio.rest.util.ResponseUtils.handleFailure;

public class PostgresFunctionExecutionService {

  public Future<Void> runBudgetEncumbrancesRolloverScript(LedgerFiscalYearRollover rollover, DBClient client) {
    Promise<Void> promise = Promise.promise();
    String schemaName = PostgresClient.convertToPsqlStandard(client.getTenantId());
    String sql = "DO\n" + "$$\n" + "begin\n" + " PERFORM %s.budget_encumbrances_rollover('%s');\n" + "end;\n"
        + "$$ LANGUAGE plpgsql;";

    client.getPgClient()
      .execute(String.format(sql, schemaName, JsonObject.mapFrom(rollover)
        .encode()), event -> {
          if (event.succeeded()) {
            promise.complete();
          } else {
            handleFailure(promise, event);
          }
        });
    return promise.future();
  }
}
