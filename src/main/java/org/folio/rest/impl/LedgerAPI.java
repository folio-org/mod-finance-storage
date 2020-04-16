package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class LedgerAPI implements FinanceStorageLedgers {
  public static final String LEDGER_TABLE = "ledger";

  private static final Logger log = LoggerFactory.getLogger(LedgerAPI.class);
  private PostgresClient pgClient;
  private String tenantId;

  public LedgerAPI(Vertx vertx, String tenantId) {
    this.tenantId = tenantId;
    pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

  @Override
  @Validate
  public void getFinanceStorageLedgers(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(LEDGER_TABLE, Ledger.class, LedgerCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageLedgersResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageLedgers(String lang, Ledger entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Tx<Ledger> tx = new Tx<>(entity, pgClient);
    vertxContext.runOnContext(event ->
      tx.startTx()
        .compose(this::saveLedger)
        .compose(this::createLedgerFiscalYearRecords)
        .compose(Tx::endTx)
        .onComplete(result -> {
          if (result.failed()) {
            HttpStatusException cause = (HttpStatusException) result.cause();
            log.error("New ledger record creation has failed: {}", cause, tx.getEntity());

            // The result of rollback operation is not so important, main failure cause is used to build the response
            tx.rollbackTransaction().onComplete(res -> HelperUtils.replyWithErrorResponse(asyncResultHandler, cause));
          } else {
            log.info("New ledger record {} and associated data were successfully created", tx.getEntity());
            asyncResultHandler.handle(succeededFuture(PostFinanceStorageLedgersResponse
              .respond201WithApplicationJson(result.result().getEntity(), PostFinanceStorageLedgersResponse.headersFor201()
                .withLocation(HelperUtils.getEndpoint(FinanceStorageLedgers.class) + result.result().getEntity().getId()))));
          }
        })
    );
  }

  @Override
  @Validate
  public void getFinanceStorageLedgersById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(LEDGER_TABLE, Ledger.class, id, okapiHeaders, vertxContext, GetFinanceStorageLedgersByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageLedgersById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Tx<String> tx = new Tx<>(id, pgClient);
    CriterionBuilder criterionBuilder = new CriterionBuilder().with("ledgerId", id);
    vertxContext.runOnContext(event ->
      tx.startTx()
      .compose(ok -> new FinanceStorageAPI().deleteLedgerFiscalYearRecords(tx,
        criterionBuilder.build()))
      .compose(ok -> HelperUtils.deleteRecordById(tx, LEDGER_TABLE))
      .compose(Tx::endTx)
      .onComplete(handleNoContentResponse(asyncResultHandler, tx, "Ledger {} {} deleted"))
    );
  }

  @Override
  @Validate
  public void putFinanceStorageLedgersById(String id, String lang, Ledger ledger, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    ledger.setId(id);
    vertxContext.runOnContext(event ->
      isLedgerStatusChanged(ledger)
        .onComplete(result -> {
          if (result.failed()) {
            HttpStatusException cause = (HttpStatusException) result.cause();
            log.error("Update of the ledger record {} has failed", cause, ledger.getId());
            HelperUtils.replyWithErrorResponse(asyncResultHandler, cause);
          } else if (result.result() == null) {
            asyncResultHandler.handle(succeededFuture(PutFinanceStorageLedgersByIdResponse.respond404WithTextPlain("Not found")));
          } else if (Boolean.TRUE.equals(result.result())) {
            handleLedgerStatusUpdate(ledger, asyncResultHandler);
          } else {
            PgUtil.put(LEDGER_TABLE, ledger, id, okapiHeaders, vertxContext, PutFinanceStorageLedgersByIdResponse.class, asyncResultHandler);
          }
        })
    );
  }

  private void handleLedgerStatusUpdate(Ledger ledger, Handler<AsyncResult<Response>> asyncResultHandler) {
    Tx<Ledger> tx = new Tx<>(ledger, pgClient);
    tx.startTx()
      .compose(this::updateLedger)
      .compose(this::updateRelatedFunds)
      .compose(fundIds -> updateRelatedBudgets(tx, fundIds))
      .compose(Tx::endTx)
      .onComplete(handleNoContentResponse(asyncResultHandler, tx, "Ledger {} {} updated"));
  }

  private Future<Tx<Ledger>> updateRelatedBudgets(Tx<Ledger> tx, List<String> fundIds) {
    Promise<Tx<Ledger>> promise = Promise.promise();
    Ledger ledger = tx.getEntity();
    if (CollectionUtils.isEmpty(fundIds)) {
      promise.complete(tx);
    } else {
      String fullBudgetTableName = getFullTableName(tenantId, BUDGET_TABLE);
      String fullFYTableName = getFullTableName(tenantId, FISCAL_YEAR_TABLE);
      String queryPlaceHolders = fundIds.stream().map(s -> "?").collect(Collectors.joining(", ", "", ""));
      JsonArray params = new JsonArray();
      params.add("\"" + ledger.getLedgerStatus() + "\"");
      params.addAll(new JsonArray(fundIds));

      String sql = "UPDATE " + fullBudgetTableName + " SET jsonb = jsonb_set(jsonb,'{budgetStatus}', ?::jsonb) " +
        "WHERE ((fundId IN (" + queryPlaceHolders + "))" +
        " AND (fiscalYearId IN " +
        "(SELECT id FROM " + fullFYTableName + " WHERE  current_date between (jsonb->>'periodStart')::timestamp " +
        "AND (jsonb->>'periodEnd')::timestamp)));";
      tx.getPgClient().execute(tx.getConnection(), sql, params, event -> {
        if (event.failed()) {
          handleFailure(promise, event);
        } else {
          log.info("{} budget records are updated", event.result().getUpdated());
          promise.complete(tx);
        }
      });
    }
    return promise.future();
  }

  private Future<Boolean> isLedgerStatusChanged(Ledger ledger) {
    Promise<Boolean> promise = Promise.promise();
    pgClient.getById(LEDGER_TABLE, ledger.getId(), Ledger.class, event -> {
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

  private Future<Tx<Ledger>> updateLedger(Tx<Ledger> tx) {
    Promise<Tx<Ledger>> promise = Promise.promise();
    Ledger ledger = tx.getEntity();
    tx.getPgClient().update(tx.getConnection(), LEDGER_TABLE, ledger, "jsonb", " WHERE id='" + ledger.getId() + "'", false, event -> {
        if (event.failed()) {
        handleFailure(promise, event);
      } else {
        log.info("Ledger record {} was successfully updated", tx.getEntity());
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  private Future<List<String>> updateRelatedFunds(Tx<Ledger> tx) {
    Promise<List<String>> promise = Promise.promise();
    Ledger ledger = tx.getEntity();
    String fullFundTableName = getFullTableName(tenantId, FUND_TABLE);
    String sql = "UPDATE " + fullFundTableName + "  SET jsonb = jsonb_set(jsonb,'{fundStatus}', ?::jsonb) " +
      "WHERE (ledgerId = ?) AND (jsonb->>'fundStatus' <> ?) RETURNING id";
    JsonArray params = new JsonArray();
    params.add("\"" + ledger.getLedgerStatus() + "\"");
    params.add(ledger.getId());
    params.add(ledger.getLedgerStatus().value());

    tx.getPgClient().select(tx.getConnection(), sql, params, event -> {
      if (event.failed()) {
        handleFailure(promise, event);
      } else {
        log.info("All fund records related to ledger with id={} has been successfully updated", tx.getEntity().getId());
        List<String> ids = event.result().getResults().stream().flatMap(JsonArray::stream).map(Object::toString).collect(Collectors.toList());
        promise.complete(ids);
      }
    });
        return promise.future();
  }

  private Future<Tx<Ledger>> saveLedger(Tx<Ledger> tx) {
    Promise<Tx<Ledger>> promise = Promise.promise();

    Ledger ledger = tx.getEntity();
    if (ledger.getId() == null) {
      ledger.setId(UUID.randomUUID().toString());
    }

    log.debug("Creating new ledger record with id={}", ledger.getId());

    tx.getPgClient().save(tx.getConnection(), LEDGER_TABLE, ledger.getId(), ledger, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        promise.complete(tx);
      }
    });
    return promise.future();
  }

  private Future<Tx<Ledger>> createLedgerFiscalYearRecords(Tx<Ledger> tx) {
    Promise<Tx<Ledger>> promise = Promise.promise();
    getFiscalYears(tx)
      .map(fiscalYears -> buildLedgerFiscalYearRecords(tx.getEntity(), fiscalYears))
      .compose(ledgerFYs -> new FinanceStorageAPI().saveLedgerFiscalYearRecords(tx, ledgerFYs))
      .onComplete(result -> {
        if (result.failed()) {
          handleFailure(promise, result);
        } else {
          promise.complete(tx);
        }
      });
    return promise.future();
  }

  private List<LedgerFY> buildLedgerFiscalYearRecords(Ledger ledger, List<FiscalYear> fiscalYears) {
    return fiscalYears.stream()
      .filter(fy -> StringUtils.isNotEmpty(fy.getCurrency()))
      .map(fiscalYear -> new LedgerFY()
        // No need to generate uuids here because PostgresClient.saveBatch(...) generates ids for each record
        .withCurrency(fiscalYear.getCurrency())
        .withLedgerId(ledger.getId())
        .withFiscalYearId(fiscalYear.getId()))
      .collect(Collectors.toList());
  }

  private Future<List<FiscalYear>> getFiscalYears(Tx<Ledger> tx) {
    Promise<List<FiscalYear>> promise = Promise.promise();
    tx.getPgClient().get(tx.getConnection(), FiscalYearAPI.FISCAL_YEAR_TABLE, FiscalYear.class, new Criterion(), true, false, reply -> {
      if (reply.failed()) {
        log.error("Failed to find fiscal years");
        handleFailure(promise, reply);
      } else {
        List<FiscalYear> allFiscalYears = Optional.ofNullable(reply.result().getResults()).orElse(Collections.emptyList());
        List<FiscalYear> fiscalYearsWithSeries = getFiscalYearsWithSeries(allFiscalYears, getFiscalYearSeries(allFiscalYears, tx.getEntity().getFiscalYearOneId()));
        log.info("{} fiscal years have been found", fiscalYearsWithSeries.size());
        promise.complete(fiscalYearsWithSeries);
      }
    });
    return promise.future();
  }

  private String getFiscalYearSeries(List<FiscalYear> fiscalYears, String fiscalYearId) {
    return fiscalYears.stream()
      .filter(fiscalYear -> fiscalYear.getId().equals(fiscalYearId))
      .map(FiscalYear::getSeries)
      .findFirst().orElse(EMPTY);
  }

  private List<FiscalYear> getFiscalYearsWithSeries(List<FiscalYear> fiscalYears, String series) {
    return fiscalYears.stream()
      .filter(fiscalYear -> fiscalYear.getSeries().equals(series))
      .collect(Collectors.toList());
  }
}
