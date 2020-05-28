package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.dao.ledger.LedgerPostgresDAO.LEDGER_TABLE;
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

import io.vertx.core.Vertx;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.dao.ledgerfy.LedgerFiscalYearDAO;
import org.folio.dao.ledgerfy.LedgerFiscalYearPostgresDAO;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class LedgerAPI implements FinanceStorageLedgers {

  private static final Logger log = LoggerFactory.getLogger(LedgerAPI.class);

  @Autowired
  private LedgerFiscalYearDAO ledgerFiscalYearDAO;

  public LedgerAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
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
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    vertxContext.runOnContext(event ->
     client.startTx()
        .compose(tx1 -> saveLedger(entity, client))
        .compose(ledger -> createLedgerFiscalYearRecords(ledger,client))
        .compose(t ->client.endTx())
        .onComplete(result -> {
          if (result.failed()) {
            HttpStatusException cause = (HttpStatusException) result.cause();
            log.error("New ledger record creation has failed: {}", cause, entity);

            // The result of rollback operation is not so important, main failure cause is used to build the response
           client.rollbackTransaction().onComplete(res -> HelperUtils.replyWithErrorResponse(asyncResultHandler, cause));
          } else {
            log.info("New ledger record {} and associated data were successfully created", entity);
            asyncResultHandler.handle(succeededFuture(PostFinanceStorageLedgersResponse
              .respond201WithApplicationJson(entity, PostFinanceStorageLedgersResponse.headersFor201()
                .withLocation(HelperUtils.getEndpoint(FinanceStorageLedgers.class) + entity.getId()))));
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
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    CriterionBuilder criterionBuilder = new CriterionBuilder().with("ledgerId", id);
    vertxContext.runOnContext(event ->
     client.startTx()
      .compose(ok -> ledgerFiscalYearDAO.deleteLedgerFiscalYearRecords(criterionBuilder.build(), client))
      .compose(ok -> HelperUtils.deleteRecordById(id, client, LEDGER_TABLE))
      .compose(v -> client.endTx())
      .onComplete(handleNoContentResponse(asyncResultHandler, id, client, "Ledger {} {} deleted"))
    );
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
            log.error("Update of the ledger record {} has failed", cause, ledger.getId());
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
      String queryPlaceHolders = fundIds.stream().map(s -> "?").collect(Collectors.joining(", ", "", ""));
      JsonArray params = new JsonArray();
      params.add("\"" + ledger.getLedgerStatus() + "\"");
      params.addAll(new JsonArray(fundIds));

      String sql = "UPDATE " + fullBudgetTableName + " SET jsonb = jsonb_set(jsonb,'{budgetStatus}', ?::jsonb) " +
        "WHERE ((fundId IN (" + queryPlaceHolders + "))" +
        " AND (fiscalYearId IN " +
        "(SELECT id FROM " + fullFYTableName + " WHERE  current_date between (jsonb->>'periodStart')::timestamp " +
        "AND (jsonb->>'periodEnd')::timestamp)));";
     client.getPgClient().execute(client.getConnection(), sql, params, event -> {
        if (event.failed()) {
          handleFailure(promise, event);
        } else {
          log.info("{} budget records are updated", event.result().getUpdated());
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
    String sql = "UPDATE " + fullFundTableName + "  SET jsonb = jsonb_set(jsonb,'{fundStatus}', ?::jsonb) " +
      "WHERE (ledgerId = ?) AND (jsonb->>'fundStatus' <> ?) RETURNING id";
    JsonArray params = new JsonArray();
    params.add("\"" + ledger.getLedgerStatus() + "\"");
    params.add(ledger.getId());
    params.add(ledger.getLedgerStatus().value());

   client.getPgClient().select(client.getConnection(), sql, params, event -> {
      if (event.failed()) {
        handleFailure(promise, event);
      } else {
        log.info("All fund records related to ledger with id={} has been successfully updated", ledger.getId());
        List<String> ids = event.result().getResults().stream().flatMap(JsonArray::stream).map(Object::toString).collect(Collectors.toList());
        promise.complete(ids);
      }
    });
        return promise.future();
  }

  private Future<Ledger> saveLedger(Ledger ledger, DBClient client) {
    Promise<Ledger> promise = Promise.promise();

    if (ledger.getId() == null) {
      ledger.setId(UUID.randomUUID().toString());
    }

    log.debug("Creating new ledger record with id={}", ledger.getId());

   client.getPgClient().save(client.getConnection(), LEDGER_TABLE, ledger.getId(), ledger, reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        promise.complete(ledger);
      }
    });
    return promise.future();
  }

  private Future<Ledger> createLedgerFiscalYearRecords(Ledger ledger, DBClient client) {
    Promise<Ledger> promise = Promise.promise();
    getFiscalYears(ledger, client)
      .map(fiscalYears -> buildLedgerFiscalYearRecords(ledger, fiscalYears))
      .compose(ledgerFYs -> ledgerFiscalYearDAO.saveLedgerFiscalYearRecords(ledgerFYs, client))
      .onComplete(result -> {
        if (result.failed()) {
          handleFailure(promise, result);
        } else {
          promise.complete(ledger);
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

  private Future<List<FiscalYear>> getFiscalYears(Ledger ledger, DBClient client) {
    Promise<List<FiscalYear>> promise = Promise.promise();
   client.getPgClient().get(client.getConnection(), FiscalYearAPI.FISCAL_YEAR_TABLE, FiscalYear.class, new Criterion(), true, false, reply -> {
      if (reply.failed()) {
        log.error("Failed to find fiscal years");
        handleFailure(promise, reply);
      } else {
        List<FiscalYear> allFiscalYears = Optional.ofNullable(reply.result().getResults()).orElse(Collections.emptyList());
        List<FiscalYear> fiscalYearsWithSeries = getFiscalYearsWithSeries(allFiscalYears, getFiscalYearSeries(allFiscalYears, ledger.getFiscalYearOneId()));
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
