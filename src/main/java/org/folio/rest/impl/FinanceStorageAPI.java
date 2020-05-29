package org.folio.rest.impl;

import static org.folio.dao.ledgerfy.LedgerFiscalYearPostgresDAO.LEDGERFY_TABLE;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.LedgerFYCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerFiscalYears;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class FinanceStorageAPI implements FinanceStorageLedgerFiscalYears {

  @Override
  public void getFinanceStorageLedgerFiscalYears(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGERFY_TABLE, LedgerFY.class, LedgerFYCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageLedgerFiscalYearsResponse.class, asyncResultHandler);

  }

}
