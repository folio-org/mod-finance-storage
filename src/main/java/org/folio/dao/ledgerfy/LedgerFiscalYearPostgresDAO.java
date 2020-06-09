package org.folio.dao.ledgerfy;

import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleVoidAsyncResult;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.List;

import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.folio.rest.util.ResponseUtils;

public class LedgerFiscalYearPostgresDAO implements LedgerFiscalYearDAO {
  public static final String LEDGERFY_TABLE = "ledger_fy";

  public Future<List<LedgerFY>> getLedgerFiscalYears(Criterion criterion, DBClient client) {
    Promise<List<LedgerFY>> promise = Promise.promise();
    client.getPgClient().get(LEDGERFY_TABLE, LedgerFY.class, criterion, false, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        List<LedgerFY> ledgerFIES = reply.result()
          .getResults();
        promise.complete(ledgerFIES);
      }
    });
    return promise.future();
  }

  public Future<Void> saveLedgerFiscalYearRecords(List<LedgerFY> ledgerFYs, DBClient client) {
    if (ledgerFYs.isEmpty()) {
      return succeededFuture();
    }
    Promise<Void> promise = Promise.promise();
    client.getPgClient().saveBatch(client.getConnection(), LEDGERFY_TABLE, ledgerFYs, reply -> handleVoidAsyncResult(promise, reply));
    return promise.future();
  }

  public Future<Void> deleteLedgerFiscalYearRecords(Criterion criterion, DBClient client) {
    Promise<Void> promise = Promise.promise();
    client.getPgClient().delete(client.getConnection(), LEDGERFY_TABLE, criterion, reply -> ResponseUtils.handleVoidAsyncResult(promise, reply));
    return promise.future();
  }

  public Future<Void> updateLedgerFiscalYears(List<LedgerFY> ledgersFYears, DBClient client) {
    Promise<Void> promise = Promise.promise();
    String sql = getLedgerFyUpdateQuery(ledgersFYears, client.getTenantId());
    client.getPgClient().execute(client.getConnection(), sql, reply -> ResponseUtils.handleVoidAsyncResult(promise, reply));
    return promise.future();
  }

  private String getLedgerFyUpdateQuery(List<LedgerFY> ledgerFYs, String tenantId) {
    List<JsonObject> jsonLedgerFYs = ledgerFYs.stream().map(JsonObject::mapFrom).collect(toList());
    return String.format("UPDATE %s AS ledger_fy SET jsonb = b.jsonb FROM (VALUES  %s) AS b (id, jsonb) "
      + "WHERE b.id::uuid = ledger_fy.id;", getFullTableName(tenantId, LEDGERFY_TABLE), HelperUtils.getQueryValues(jsonLedgerFYs));
  }

}
