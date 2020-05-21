package org.folio.rest.impl;

import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleNoContentResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.FiscalYearCollection;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYears;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class FiscalYearAPI implements FinanceStorageFiscalYears {
  static final String FISCAL_YEAR_TABLE = "fiscal_year";

  private static final Logger log = LoggerFactory.getLogger(FiscalYearAPI.class);

  @Override
  @Validate
  public void getFinanceStorageFiscalYears(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(FISCAL_YEAR_TABLE, FiscalYear.class, FiscalYearCollection.class, query, offset, limit, okapiHeaders, vertxContext,
        GetFinanceStorageFiscalYearsResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void postFinanceStorageFiscalYears(String lang, FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    vertxContext.runOnContext(event ->
     client.startTx()
        .compose(v -> saveFiscalYear(entity, client))
        .compose(fiscalYear -> createLedgerFiscalYearRecords(fiscalYear, client))
        .compose(v -> client.endTx())
        .onComplete(result -> {
          if (result.failed()) {
            HttpStatusException cause = (HttpStatusException) result.cause();
            log.error("New fiscal year record creation has failed: {}", cause, entity);

            // The result of rollback operation is not so important, main failure cause is used to build the response
           client.rollbackTransaction().onComplete(res -> HelperUtils.replyWithErrorResponse(asyncResultHandler, cause));
          } else {
            log.info("New fiscal year record {} and associated data were successfully created", entity);
            asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageFiscalYearsResponse
              .respond201WithApplicationJson(entity, PostFinanceStorageFiscalYearsResponse.headersFor201()
                .withLocation(HelperUtils.getEndpoint(FinanceStorageFiscalYears.class) + entity.getId()))));
          }
        })
    );
  }

  @Override
  @Validate
  public void getFinanceStorageFiscalYearsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FISCAL_YEAR_TABLE, FiscalYear.class, id, okapiHeaders, vertxContext, GetFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFiscalYearsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    DBClient client = new DBClient(vertxContext, okapiHeaders);
    Criterion criterion = new CriterionBuilder()
      .with("fiscalYearId", id).build();

    vertxContext.runOnContext(event ->
     client.startTx()
        .compose(ok -> new FinanceStorageAPI().deleteLedgerFiscalYearRecords(client, criterion))
        .compose(ok -> HelperUtils.deleteRecordById(id,client, FISCAL_YEAR_TABLE))
        .compose(v -> client.endTx())
        .onComplete(handleNoContentResponse(asyncResultHandler, id,  client,"Fiscal year {} {} deleted"))
    );
  }

  @Override
  @Validate
  public void putFinanceStorageFiscalYearsById(String id, String lang, FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FISCAL_YEAR_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  private Future<FiscalYear> saveFiscalYear(FiscalYear fiscalYear, DBClient client) {
    Promise<FiscalYear> future = Promise.promise();

    if (fiscalYear.getId() == null) {
      fiscalYear.setId(UUID.randomUUID().toString());
    }

    log.debug("Creating new fiscal year record with id={}", fiscalYear.getId());

   client.getPgClient().save(client.getConnection(), FISCAL_YEAR_TABLE, fiscalYear.getId(), fiscalYear, reply -> {
      if (reply.failed()) {
        handleFailure(future, reply);
      } else {
        log.info("New fiscal year record with id={} has been successfully created", fiscalYear.getId());
        future.complete(fiscalYear);
      }
    });
    return future.future();
  }

  private Future<Void> createLedgerFiscalYearRecords(FiscalYear fiscalYear, DBClient client) {
    // In case no currency, do not create any related records
    if (StringUtils.isEmpty(fiscalYear.getCurrency())) {
      return Future.succeededFuture();
    }

    Promise<Void> promise = Promise.promise();
    getFiscalYearIdsForSeries(client, fiscalYear.getSeries())
      .compose(fiscalYearIds -> getLedgers(client, fiscalYearIds)
        .map(ledgers -> buildLedgerFiscalYearRecords(fiscalYear, ledgers))
        .compose(ledgerFYs -> new FinanceStorageAPI().saveLedgerFiscalYearRecords(client, ledgerFYs))
        .onComplete(result -> {
          if (result.failed()) {
            handleFailure(promise, result);
          } else {
            promise.complete();
          }
        })
    );
    return promise.future();
  }

  private List<LedgerFY> buildLedgerFiscalYearRecords(FiscalYear fiscalYear, List<Ledger> ledgers) {
    return ledgers.stream()
      .map(ledger -> new LedgerFY()
        // No need to generate uuids here because PostgresClient.saveBatch(...) generates ids for each record
        .withCurrency(fiscalYear.getCurrency())
        .withLedgerId(ledger.getId())
        .withFiscalYearId(fiscalYear.getId()))
      .collect(Collectors.toList());
  }

  private Future<List<Ledger>> getLedgers(DBClient client, List<String> fiscalYearIds) {
    Promise<List<Ledger>> promise = Promise.promise();
   client.getPgClient().get(client.getConnection(), LedgerAPI.LEDGER_TABLE, Ledger.class, new Criterion(), true, false, reply -> {
      if (reply.failed()) {
        log.error("Failed to find ledgers");
        handleFailure(promise, reply);
      } else {
        List<Ledger> allLedgers = Optional.ofNullable(reply.result().getResults()).orElse(Collections.emptyList());
        List<Ledger> ledgersWithSeriesInUse = allLedgers.stream()
          .filter(ledger -> fiscalYearIds.contains(ledger.getFiscalYearOneId()))
          .collect(Collectors.toList());
        log.info("{} ledgers have been found", ledgersWithSeriesInUse.size());
        promise.complete(ledgersWithSeriesInUse);
      }
    });
    return promise.future();
  }

  private Future<List<String>> getFiscalYearIdsForSeries(DBClient client, String series) {
    Promise<List<String>> promise = Promise.promise();
    CriterionBuilder criterionBuilder = new CriterionBuilder();
    Criterion criterion = criterionBuilder.withJson("series", "=", series).build();
   client.getPgClient().get(client.getConnection(), FISCAL_YEAR_TABLE, FiscalYear.class, criterion, true, false, reply ->{
      if (reply.failed()) {
        log.error("Failed to find fiscal year Ids");
        handleFailure(promise, reply);
      } else {
        List<FiscalYear> fiscalYears = Optional.ofNullable(reply.result().getResults()).orElse(Collections.emptyList());
        List<String> ids = fiscalYears.stream()
          .map(FiscalYear::getId)
          .collect(Collectors.toList());
        log.info("{} fiscal year Ids were found", ids.size());
        promise.complete(ids);
      }
    });
    return promise.future();
  }
}
