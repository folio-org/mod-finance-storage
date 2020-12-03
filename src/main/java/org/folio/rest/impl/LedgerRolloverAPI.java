package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerRollovers;
import org.folio.rest.persist.PgUtil;

public class LedgerRolloverAPI implements FinanceStorageLedgerRollovers {

  private static final Logger log = LoggerFactory.getLogger(LedgerRolloverAPI.class);
  private static final String LEDGER_FISCAL_YEAR_ROLLOVER_TABLE = "ledger_fiscal_year_rollover";

  @Override
  @Validate
  public void getFinanceStorageLedgerRollovers(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, LedgerFiscalYearRollover.class, LedgerFiscalYearRolloverCollection.class, query, offset, limit, okapiHeaders, vertxContext,
      GetFinanceStorageLedgerRolloversResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageLedgerRollovers(String lang, LedgerFiscalYearRollover entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageLedgerRolloversResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageLedgerRolloversById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, LedgerFiscalYearRollover.class, id, okapiHeaders, vertxContext, GetFinanceStorageLedgerRolloversByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageLedgerRolloversById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageLedgerRolloversByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageLedgerRolloversById(String id, String lang, LedgerFiscalYearRollover entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(LEDGER_FISCAL_YEAR_ROLLOVER_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageLedgerRolloversByIdResponse.class, asyncResultHandler);
  }
}
