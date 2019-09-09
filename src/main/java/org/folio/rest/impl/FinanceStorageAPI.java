package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.LedgerFYCollection;
import org.folio.rest.jaxrs.resource.FinanceStorage;
import org.folio.rest.persist.PgUtil;

public class FinanceStorageAPI implements FinanceStorage {
  private static final String LEDGERFY_TABLE = "ledgerFY";

  @Override
  public void getFinanceStorageLedgerFiscalYears(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGERFY_TABLE, LedgerFY.class, LedgerFYCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageLedgerFiscalYearsResponse.class, asyncResultHandler);

  }
}
