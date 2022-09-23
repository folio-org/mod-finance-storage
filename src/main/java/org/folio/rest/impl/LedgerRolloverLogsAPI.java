package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverLog;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverLogCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerRolloversLogs;

import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;

public class LedgerRolloverLogsAPI implements FinanceStorageLedgerRolloversLogs {

  public static final String LEDGER_ROLLOVER_LOGS_VIEW = "ledger_rollover_logs_view";

  @Override
  public void getFinanceStorageLedgerRolloversLogs(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGER_ROLLOVER_LOGS_VIEW, LedgerFiscalYearRolloverLog.class, LedgerFiscalYearRolloverLogCollection.class, query,
      offset, limit, okapiHeaders, vertxContext, FinanceStorageLedgerRolloversLogs.GetFinanceStorageLedgerRolloversLogsResponse.class, asyncResultHandler);
  }

  @Override
  public void getFinanceStorageLedgerRolloversLogsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_ROLLOVER_LOGS_VIEW, LedgerFiscalYearRolloverLog.class, id, okapiHeaders, vertxContext, FinanceStorageLedgerRolloversLogs.GetFinanceStorageLedgerRolloversLogsByIdResponse.class, asyncResultHandler);
  }
}
