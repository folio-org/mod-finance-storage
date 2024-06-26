package org.folio.service;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.PostgresClient;
import org.folio.utils.ObjectMapper;

import static org.folio.rest.persist.HelperUtils.getFullTableName;

public class PostgresFunctionExecutionService {

  private static final Logger logger = LogManager.getLogger(PostgresFunctionExecutionService.class);

  public Future<Void> runBudgetEncumbrancesRolloverScript(LedgerFiscalYearRollover rollover, DBConn conn) {
    logger.debug("runBudgetEncumbrancesRolloverScript:: Trying to run budget encumbrances rollover script");
    String schemaName = PostgresClient.convertToPsqlStandard(conn.getTenantId());
    String sql = String.format("SELECT %s.budget_encumbrances_rollover($1)", schemaName);
    return conn.execute(sql, Tuple.of(ObjectMapper.valueAsString(rollover)))
      .onSuccess(rowSet -> logger.info("runBudgetEncumbrancesRolloverScript:: Budget encumbrances rollover script successfully ran"))
      .onFailure(e -> logger.error("runBudgetEncumbrancesRolloverScript:: Running budget encumbrances rollover script failed", e))
      .mapEmpty();
  }

  public Future<Void> dropTable(String tableName, boolean isTemporary, DBConn conn) {
    logger.debug("dropTable:: Trying to drop table with name {}", tableName);
    String preparedTableName = isTemporary ? tableName : getFullTableName(conn.getTenantId(), tableName);
    String sql = "DROP TABLE IF EXISTS %s;";

    return conn.execute(String.format(sql, preparedTableName))
      .onSuccess(rowSet -> logger.info("dropTable:: The table named {} successfully dropped", tableName))
      .onFailure(e -> logger.error("dropTable:: Dropping table named {} failed", tableName, e))
      .mapEmpty();
  }
}
