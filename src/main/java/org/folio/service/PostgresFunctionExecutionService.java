package org.folio.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.service.budget.BudgetService;

import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;

public class PostgresFunctionExecutionService {

  private static final Logger logger = LogManager.getLogger(PostgresFunctionExecutionService.class);

  public Future<Void> runBudgetEncumbrancesRolloverScript(LedgerFiscalYearRollover rollover, DBClient client) {
    logger.debug("runBudgetEncumbrancesRolloverScript:: Trying to run budget encumbrances rollover script");
    Promise<Void> promise = Promise.promise();
    String schemaName = PostgresClient.convertToPsqlStandard(client.getTenantId());
    String sql = "DO\n" + "$$\n" + "begin\n" + " PERFORM %s.budget_encumbrances_rollover('%s');\n" + "end;\n"
        + "$$ LANGUAGE plpgsql;";

    client.getPgClient()
      .execute(String.format(sql, schemaName, JsonObject.mapFrom(rollover)
        .encode()), event -> {
          if (event.succeeded()) {
            logger.debug("runBudgetEncumbrancesRolloverScript:: Budget encumbrances rollover script successfully ran");
            promise.complete();
          } else {
            logger.error("runBudgetEncumbrancesRolloverScript:: Running budget encumbrances rollover script failed", event.cause());
            handleFailure(promise, event);
          }
        });
    return promise.future();
  }

  public Future<Void> dropTable(String tableName, boolean isTemporary, DBClient client) {
    logger.debug("dropTable:: Trying to drop table with name {}", tableName);
    Promise<Void> promise = Promise.promise();
    String preparedTableName = isTemporary ? tableName : getFullTableName(client.getTenantId(), tableName);
    String sql = "DROP TABLE IF EXISTS %s;";

    client.getPgClient()
      .execute(String.format(sql, preparedTableName), event -> {
        if (event.succeeded()) {
          logger.info("dropTable:: The table named {} successfully dropped", tableName);
          promise.complete();
        } else {
          logger.info("dropTable:: Dropping table named {} failed", tableName);
          handleFailure(promise, event);
        }
      });
    return promise.future();
  }
}
