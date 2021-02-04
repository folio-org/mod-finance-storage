package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.dao.ledger.LedgerPostgresDAO.LEDGER_TABLE;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.ws.rs.core.Response;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.ArrayTuple;

public class LedgerAPI implements FinanceStorageLedgers {

  private static final Logger log = LogManager.getLogger(LedgerAPI.class);

  @Override
  @Validate
  public void getFinanceStorageLedgers(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGER_TABLE, Ledger.class, LedgerCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageLedgersResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageLedgers(String lang, Ledger entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(LEDGER_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageLedgersResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageLedgersById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_TABLE, Ledger.class, id, okapiHeaders, vertxContext, GetFinanceStorageLedgersByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageLedgersById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(LEDGER_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageLedgersByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageLedgersById(String id, String lang, Ledger ledger, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    ledger.setId(id);
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    vertxContext.runOnContext(event ->
      isLedgerStatusChanged(ledger, client)
        .onComplete(result -> {
          if (result.failed()) {
            HttpStatusException cause = (HttpStatusException) result.cause();
            log.error("Update of the ledger record {} has failed", ledger.getId(), cause);
            HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
          } else if (result.result() == null) {
            asyncResultHandler.handle(succeededFuture(PutFinanceStorageLedgersByIdResponse.respond404WithTextPlain("Not found")));
          } else if (Boolean.TRUE.equals(result.result())) {
            handleLedgerStatusUpdate(ledger, client, asyncResultHandler);
          } else {
            PgUtil.put(LEDGER_TABLE, ledger, id, okapiHeaders, vertxContext, PutFinanceStorageLedgersByIdResponse.class, asyncResultHandler);
          }
        })
    );
  }

  private void handleLedgerStatusUpdate(Ledger ledger, DBClient client, Handler<AsyncResult<Response>> asyncResultHandler) {
   client.startTx()
      .compose(v -> updateLedger(ledger, client))
      .compose(v -> updateRelatedFunds(ledger,client))
      .compose(fundIds -> updateRelatedBudgets(fundIds, ledger,client))
      .compose(v ->client.endTx())
      .onComplete(handleNoContentResponse(asyncResultHandler, ledger.getId(),client, "Ledger {} {} updated"));
  }

  private Future<Void> updateRelatedBudgets(List<String> fundIds, Ledger ledger,  DBClient client) {
    Promise<Void> promise = Promise.promise();
    if (CollectionUtils.isEmpty(fundIds)) {
      promise.complete();
    } else {
      String fullBudgetTableName = getFullTableName(client.getTenantId(), BUDGET_TABLE);
      String fullFYTableName = getFullTableName(client.getTenantId(), FISCAL_YEAR_TABLE);

      String queryPlaceHolders = IntStream.range(0, fundIds.size())
        .map(i -> i + 2)
        .mapToObj(i -> "$" + i)
        .collect(Collectors.joining(","));

      ArrayTuple params = new ArrayTuple(fundIds.size() + 1);
      params.addValue(ledger.getLedgerStatus().value());
      fundIds.forEach(fundId -> params.addValue(UUID.fromString(fundId)));

      String sql = "UPDATE " + fullBudgetTableName + " SET jsonb = jsonb_set(jsonb,'{budgetStatus}', $1) " +
        "WHERE ((fundId IN (" + queryPlaceHolders + "))" +
        " AND (fiscalYearId IN " +
        "(SELECT id FROM " + fullFYTableName + " WHERE  current_date between (jsonb->>'periodStart')::timestamp " +
        "AND (jsonb->>'periodEnd')::timestamp)));";
     client.getPgClient().execute(client.getConnection(), sql, params, event -> {
        if (event.failed()) {
          handleFailure(promise, event);
        } else {
          log.info("{} budget records are updated", event.result().rowCount());
          promise.complete();
        }
      });
    }
    return promise.future();
  }

  private Future<Boolean> isLedgerStatusChanged(Ledger ledger, DBClient client) {
    Promise<Boolean> promise = Promise.promise();
    client.getPgClient().getById(LEDGER_TABLE, ledger.getId(), Ledger.class, event -> {
      if (event.failed()) {
        handleFailure(promise, event);
      } else {
        if (event.result() != null) {
          promise.complete(event.result().getLedgerStatus() != ledger.getLedgerStatus());
        } else {
          promise.complete(null);
        }
      }
    });
    return promise.future();
  }

  private Future<Void> updateLedger(Ledger ledger, DBClient client) {
    Promise<Void> promise = Promise.promise();

   client.getPgClient().update(client.getConnection(), LEDGER_TABLE, ledger, "jsonb", " WHERE id='" + ledger.getId() + "'", false, event -> {
        if (event.failed()) {
        handleFailure(promise, event);
      } else {
        log.info("Ledger record {} was successfully updated", ledger);
        promise.complete();
      }
    });
    return promise.future();
  }

  private Future<List<String>> updateRelatedFunds(Ledger ledger, DBClient client) {
    Promise<List<String>> promise = Promise.promise();

    String fullFundTableName = getFullTableName(client.getTenantId(), FUND_TABLE);
    String sql = "UPDATE " + fullFundTableName + "  SET jsonb = jsonb_set(jsonb,'{fundStatus}', $1) " +
      "WHERE (ledgerId = $2) AND (jsonb->>'fundStatus' <> $3) RETURNING id";

   client.getPgClient().select(client.getConnection(), sql,
     Tuple.of(ledger.getLedgerStatus().value(), UUID.fromString(ledger.getId()), ledger.getLedgerStatus().value()), event -> {
      if (event.failed()) {
        handleFailure(promise, event);
      } else {
        log.info("All fund records related to ledger with id={} has been successfully updated", ledger.getId());
        List<String> ids = new ArrayList<>();
        event.result().spliterator().forEachRemaining(row -> ids.add(row.getUUID(0).toString()));
        promise.complete(ids);
      }
    });
    return promise.future();
  }


}
