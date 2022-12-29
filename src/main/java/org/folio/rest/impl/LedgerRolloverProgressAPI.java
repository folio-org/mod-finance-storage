package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgressCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerRolloversProgress;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class LedgerRolloverProgressAPI implements FinanceStorageLedgerRolloversProgress {

  public static final String LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE = "ledger_fiscal_year_rollover_progress";

  @Override
  @Validate
  public void getFinanceStorageLedgerRolloversProgress(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, LedgerFiscalYearRolloverProgress.class, LedgerFiscalYearRolloverProgressCollection.class, query,
      offset, limit, okapiHeaders, vertxContext, GetFinanceStorageLedgerRolloversProgressResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageLedgerRolloversProgress(LedgerFiscalYearRolloverProgress entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageLedgerRolloversProgressResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageLedgerRolloversProgressById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, LedgerFiscalYearRolloverProgress.class, id, okapiHeaders, vertxContext, GetFinanceStorageLedgerRolloversProgressByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageLedgerRolloversProgressById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageLedgerRolloversProgressByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageLedgerRolloversProgressById(String id, LedgerFiscalYearRolloverProgress entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageLedgerRolloversProgressByIdResponse.class, asyncResultHandler);
  }
}
