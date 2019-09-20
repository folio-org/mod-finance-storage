package org.folio.rest.impl;

import java.time.Instant;
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
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class FiscalYearAPI implements FinanceStorageFiscalYears {
  static final String FISCAL_YEAR_TABLE = "fiscal_year";

  private static final Logger log = LoggerFactory.getLogger(FiscalYearAPI.class);
  private PostgresClient pgClient;

  public FiscalYearAPI(Vertx vertx, String tenantId) {
    pgClient = PostgresClient.getInstance(vertx, tenantId);
  }

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
    Tx<FiscalYear> tx = new Tx<>(entity, pgClient);
    HelperUtils.startTx(tx)
      .compose(this::saveFiscalYear)
      .compose(this::createLedgerFiscalYearRecords)
      .compose(HelperUtils::endTx)
      .setHandler(result -> {
        if (result.failed()) {
          HttpStatusException cause = (HttpStatusException) result.cause();
          log.error("New fiscal year record creation has failed: {}", cause, tx.getEntity());

          // The result of rollback operation is not so important, main failure cause is used to build the response
          HelperUtils.rollbackTransaction(tx).setHandler(res -> HelperUtils.replyWithErrorResponse(asyncResultHandler, cause));
        } else {
          log.info("New fiscal year record {} and associated data were successfully created", tx.getEntity());
          asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageFiscalYearsResponse
            .respond201WithApplicationJson(result.result().getEntity(), PostFinanceStorageFiscalYearsResponse.headersFor201()
              .withLocation(HelperUtils.getEndpoint(FinanceStorageFiscalYears.class) + result.result().getEntity().getId()))));
        }
      });
  }

  @Override
  @Validate
  public void getFinanceStorageFiscalYearsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(FISCAL_YEAR_TABLE, FiscalYear.class, id, okapiHeaders, vertxContext, GetFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  @Override
  @Validate
  public void deleteFinanceStorageFiscalYearsById(String id, String lang, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Tx<String> tx = new Tx<>(id, pgClient);
    HelperUtils.startTx(tx)
      .compose(ok -> new FinanceStorageAPI().deleteLedgerFiscalYearRecords(tx,
          HelperUtils.getCriterionByFieldNameAndValue("fiscalYearId", "=", id)))
      .compose(ok -> HelperUtils.deleteRecordById(tx, FISCAL_YEAR_TABLE))
      .compose(HelperUtils::endTx)
      .setHandler(result -> {
        if (result.failed()) {
          HttpStatusException cause = (HttpStatusException) result.cause();
          log.error("Deletion of the fiscal year record {} has failed", cause, tx.getEntity());

          // The result of rollback operation is not so important, main failure cause is used to build the response
          HelperUtils.rollbackTransaction(tx).setHandler(res -> HelperUtils.replyWithErrorResponse(asyncResultHandler, cause));
        } else {
          log.info("Fiscal year record {} and associated data were successfully deleted", tx.getEntity());
          asyncResultHandler.handle(Future.succeededFuture(DeleteFinanceStorageFiscalYearsByIdResponse.respond204()));
        }
      });
  }

  @Override
  @Validate
  public void putFinanceStorageFiscalYearsById(String id, String lang, FiscalYear entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(FISCAL_YEAR_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageFiscalYearsByIdResponse.class, asyncResultHandler);
  }

  private Future<Tx<FiscalYear>> saveFiscalYear(Tx<FiscalYear> tx) {
    Future<Tx<FiscalYear>> future = Future.future();

    FiscalYear fiscalYear = tx.getEntity();
    if (fiscalYear.getId() == null) {
      fiscalYear.setId(UUID.randomUUID().toString());
    }

    log.debug("Creating new fiscal year record with id={}", fiscalYear.getId());

    pgClient.save(tx.getConnection(), FISCAL_YEAR_TABLE, fiscalYear.getId(), fiscalYear, reply -> {
      if(reply.failed()) {
        HelperUtils.handleFailure(future, reply);
      } else {
        log.info("New fiscal year record with id={} has been successfully created", fiscalYear.getId());
        future.complete(tx);
      }
    });
    return future;
  }

  private Future<Tx<FiscalYear>> createLedgerFiscalYearRecords(Tx<FiscalYear> tx) {
    FiscalYear fiscalYear = tx.getEntity();

    // In case no currency or period's end date of the fiscal year is for some reason in past, do not create any related records
    if (StringUtils.isEmpty(fiscalYear.getCurrency()) || fiscalYear.getPeriodEnd().toInstant().isBefore(Instant.now())) {
      return Future.succeededFuture(tx);
    }

    Future<Tx<FiscalYear>> future = Future.future();
    getLedgers(tx)
      .map(ledgers -> buildLedgerFiscalYearRecords(fiscalYear, ledgers))
      .compose(ledgerFYs -> new FinanceStorageAPI().saveLedgerFiscalYearRecords(tx, ledgerFYs))
      .setHandler(result -> {
        if (result.failed()) {
          HelperUtils.handleFailure(future, result);
        } else {
          future.complete(tx);
        }
      });
    return future;
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

  private Future<List<Ledger>> getLedgers(Tx<FiscalYear> tx) {
    Future<List<Ledger>> future = Future.future();
    tx.getPgClient().get(tx.getConnection(), LedgerAPI.LEDGER_TABLE, Ledger.class, new Criterion(), true, true, reply -> {
      if(reply.failed()) {
        log.error("Failed to find ledgers");
        HelperUtils.handleFailure(future, reply);
      } else {
        List<Ledger> results = Optional.ofNullable(reply.result().getResults()).orElse(Collections.emptyList());
        log.info("{} ledgers found have been found", results.size());
        future.complete(results);
      }
    });
    return future;
  }
}
