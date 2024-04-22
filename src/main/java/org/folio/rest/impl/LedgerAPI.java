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

import org.folio.rest.exception.HttpException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.util.ResponseUtils;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.ArrayTuple;

public class LedgerAPI implements FinanceStorageLedgers {

  private static final Logger logger = LogManager.getLogger(LedgerAPI.class);

  @Override
  @Validate
  public void getFinanceStorageLedgers(String query, String totalRecords, int offset, int limit, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGER_TABLE, Ledger.class, LedgerCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageLedgersResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageLedgers(Ledger entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(LEDGER_TABLE, entity, okapiHeaders, vertxContext, PostFinanceStorageLedgersResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void getFinanceStorageLedgersById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_TABLE, Ledger.class, id, okapiHeaders, vertxContext, GetFinanceStorageLedgersByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageLedgersById(String id, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(LEDGER_TABLE, id, okapiHeaders, vertxContext, DeleteFinanceStorageLedgersByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void putFinanceStorageLedgersById(String id, Ledger ledger, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    logger.debug("Trying to update finance storage ledger by id {}", id);
    ledger.setId(id);
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    vertxContext.runOnContext(event ->
      isLedgerStatusChanged(ledger, client)
        .onComplete(result -> {
          if (result.failed()) {
            HttpException cause = (HttpException) result.cause();
            logger.error("Updating finance storage ledger by with id {} failed", id, cause);
            HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
          } else if (result.result() == null) {
            logger.warn("Finance storage ledger with id {} not found", id);
            asyncResultHandler.handle(succeededFuture(PutFinanceStorageLedgersByIdResponse.respond404WithTextPlain("Not found")));
          } else if (Boolean.TRUE.equals(result.result())) {
            handleLedgerStatusUpdate(ledger, client).onComplete(asyncResultHandler);
          } else {
            PgUtil.put(LEDGER_TABLE, ledger, id, okapiHeaders, vertxContext, PutFinanceStorageLedgersByIdResponse.class, asyncResultHandler);
          }
        })
    );
  }

  private Future<Response> handleLedgerStatusUpdate(Ledger ledger, DBClient client) {
    return client.withTrans(conn -> updateLedger(ledger, conn)
            .compose(v -> updateRelatedFunds(ledger, conn))
            .compose(fundIds -> updateRelatedBudgets(fundIds, ledger, conn)))
        .transform(result -> handleNoContentResponse(result, ledger.getId(), "Ledger {} {} updated"));
  }

  private Future<Void> updateRelatedBudgets(List<String> fundIds, Ledger ledger, DBConn conn) {
    if (CollectionUtils.isEmpty(fundIds)) {
      return Future.succeededFuture();
    }
    String fullBudgetTableName = getFullTableName(conn.getTenantId(), BUDGET_TABLE);
    String fullFYTableName = getFullTableName(conn.getTenantId(), FISCAL_YEAR_TABLE);
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
    return conn.execute(sql, params)
        .onSuccess(rowSet -> logger.info("{} budget records are updated", rowSet.rowCount()))
        .mapEmpty();
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

  private Future<Void> updateLedger(Ledger ledger, DBConn dbConn) {
    return dbConn.update(LEDGER_TABLE, ledger, ledger.getId())
        .recover(ResponseUtils::handleFailure)
        .onSuccess(x -> logger.info("Ledger record {} was successfully updated", ledger))
        .mapEmpty();
  }

  private Future<List<String>> updateRelatedFunds(Ledger ledger, DBConn conn) {
    String fullFundTableName = getFullTableName(conn.getTenantId(), FUND_TABLE);
    String sql = "UPDATE " + fullFundTableName + "  SET jsonb = jsonb_set(jsonb,'{fundStatus}', $1) " +
      "WHERE (ledgerId = $2) AND (jsonb->>'fundStatus' <> $3) RETURNING id";

    return conn.execute(sql, Tuple.of(
            ledger.getLedgerStatus().value(), UUID.fromString(ledger.getId()), ledger.getLedgerStatus().value()))
        .map(result -> {
          logger.info("All fund records related to ledger with id={} has been successfully updated", ledger.getId());
          List<String> ids = new ArrayList<>();
          result.spliterator().forEachRemaining(row -> ids.add(row.getUUID(0).toString()));
          return ids;
        });
  }

}
