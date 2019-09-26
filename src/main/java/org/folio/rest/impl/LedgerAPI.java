package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.impl.HttpStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.LedgerCollection;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;

public class LedgerAPI implements FinanceStorageLedgers {
  static final String LEDGER_TABLE = "ledger";

  private static final Logger log = LoggerFactory.getLogger(LedgerAPI.class);
  private PostgresClient pgClient;

  public LedgerAPI(Vertx vertx, String tenantId) {
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
    HelperUtils.startTx(tx)
      .compose(this::saveLedger)
      .compose(this::createLedgerFiscalYearRecords)
      .compose(HelperUtils::endTx)
      .setHandler(result -> {
        if (result.failed()) {
          HttpStatusException cause = (HttpStatusException) result.cause();
          log.error("New ledger record creation has failed: {}", cause, tx.getEntity());

          // The result of rollback operation is not so important, main failure cause is used to build the response
          HelperUtils.rollbackTransaction(tx).setHandler(res -> HelperUtils.replyWithErrorResponse(asyncResultHandler, cause));
        } else {
          log.info("New ledger record {} and associated data were successfully created", tx.getEntity());
          asyncResultHandler.handle(Future.succeededFuture(PostFinanceStorageLedgersResponse
            .respond201WithApplicationJson(result.result().getEntity(), PostFinanceStorageLedgersResponse.headersFor201()
              .withLocation(HelperUtils.getEndpoint(FinanceStorageLedgers.class) + result.result().getEntity().getId()))));
        }
      });
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
    HelperUtils.startTx(tx)
      .compose(ok -> new FinanceStorageAPI().deleteLedgerFiscalYearRecords(tx,
        HelperUtils.getCriterionByFieldNameAndValue("ledgerId", "=", id)))
      .compose(ok -> HelperUtils.deleteRecordById(tx, LEDGER_TABLE))
      .compose(HelperUtils::endTx)
      .setHandler(result -> {
        if (result.failed()) {
          HttpStatusException cause = (HttpStatusException) result.cause();
          log.error("Deletion of the ledger record {} has failed", cause, tx.getEntity());

          // The result of rollback operation is not so important, main failure cause is used to build the response
          HelperUtils.rollbackTransaction(tx).setHandler(res -> HelperUtils.replyWithErrorResponse(asyncResultHandler, cause));
        } else {
          log.info("Ledger record {} and associated data were successfully deleted", tx.getEntity());
          asyncResultHandler.handle(Future.succeededFuture(DeleteFinanceStorageLedgersByIdResponse.respond204()));
        }
      });
  }

  @Override
  @Validate
  public void putFinanceStorageLedgersById(String id, String lang, Ledger entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(LEDGER_TABLE, entity, id, okapiHeaders, vertxContext, PutFinanceStorageLedgersByIdResponse.class, asyncResultHandler);
  }

  private Future<Tx<Ledger>> saveLedger(Tx<Ledger> tx) {
    Future<Tx<Ledger>> future = Future.future();

    Ledger ledger = tx.getEntity();
    if (ledger.getId() == null) {
      ledger.setId(UUID.randomUUID().toString());
    }

    log.debug("Creating new ledger record with id={}", ledger.getId());

    pgClient.save(tx.getConnection(), LEDGER_TABLE, ledger.getId(), ledger, reply -> {
      if (reply.failed()) {
        HelperUtils.handleFailure(future, reply);
      } else {
        log.info("New ledger record with id={} has been successfully created", ledger.getId());
        future.complete(tx);
      }
    });
    return future;
  }

  private Future<Tx<Ledger>> createLedgerFiscalYearRecords(Tx<Ledger> tx) {
    Future<Tx<Ledger>> future = Future.future();
    getFiscalYears(tx)
      .map(fiscalYears -> buildLedgerFiscalYearRecords(tx.getEntity(), fiscalYears))
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
    Future<List<FiscalYear>> future = Future.future();
    Criterion criterion = HelperUtils.getCriterionByFieldNameAndValue("periodEnd", ">", Instant.now().toString());
    pgClient.get(tx.getConnection(), FiscalYearAPI.FISCAL_YEAR_TABLE, FiscalYear.class, criterion, true, true, reply -> {
      if (reply.failed()) {
        log.error("Failed to find fiscal years");
        HelperUtils.handleFailure(future, reply);
      } else {
        List<FiscalYear> results = Optional.ofNullable(reply.result().getResults()).orElse(Collections.emptyList());
        log.info("{} fiscal years have been found", results.size());
        future.complete(results);
      }
    });
    return future;
  }
}
