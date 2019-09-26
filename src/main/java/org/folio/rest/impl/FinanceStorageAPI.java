package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.model.LedgerFYCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerFiscalYears;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.Tx;

public class FinanceStorageAPI implements FinanceStorageLedgerFiscalYears {
  static final String LEDGERFY_TABLE = "ledgerFY";

  @Override
  public void getFinanceStorageLedgerFiscalYears(int offset, int limit, String query, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGERFY_TABLE, LedgerFY.class, LedgerFYCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageLedgerFiscalYearsResponse.class, asyncResultHandler);

  }

  <T> Future<Void> saveLedgerFiscalYearRecords(Tx<T> tx, List<LedgerFY> ledgerFYs) {
    if (ledgerFYs.isEmpty()) {
      return succeededFuture();
    }
    Future<Void> future = Future.future();
    tx.getPgClient().saveBatch(tx.getConnection(), LEDGERFY_TABLE, ledgerFYs, reply -> handleAsyncResult(future, reply));
    return future;
  }

  <T> Future<Void> deleteLedgerFiscalYearRecords(Tx<T> tx, Criterion criterion) {
    Future<Void> future = Future.future();
    tx.getPgClient().delete(tx.getConnection(), LEDGERFY_TABLE, criterion, reply -> handleAsyncResult(future, reply));
    return future;
  }

  private <T> void handleAsyncResult(Future<Void> future, AsyncResult<T> reply) {
    if(reply.failed()) {
      HelperUtils.handleFailure(future, reply);
    } else {
      future.complete();
    }
  }
}
