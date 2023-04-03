package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverErrorCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerRolloversErrors;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class LedgerRolloverErrorAPI implements FinanceStorageLedgerRolloversErrors {

  private static final String LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE = "ledger_fiscal_year_rollover_error";

  @Override
  @Validate
  public void getFinanceStorageLedgerRolloversErrors(String query, String totalRecords, int offset, int limit,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, LedgerFiscalYearRolloverError.class,
        LedgerFiscalYearRolloverErrorCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageLedgerRolloversErrorsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageLedgerRolloversErrors(LedgerFiscalYearRolloverError entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, entity, okapiHeaders, vertxContext,
        PostFinanceStorageLedgerRolloversErrorsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageLedgerRolloversErrorsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, LedgerFiscalYearRolloverError.class, id, okapiHeaders, vertxContext,
        GetFinanceStorageLedgerRolloversErrorsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageLedgerRolloversErrorsById(String id, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, id, okapiHeaders, vertxContext,
        DeleteFinanceStorageLedgerRolloversErrorsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageLedgerRolloversErrorsById(String id, LedgerFiscalYearRolloverError entity,
      Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE, entity, id, okapiHeaders, vertxContext,
        PutFinanceStorageLedgerRolloversErrorsByIdResponse.class, asyncResultHandler);
  }
}
