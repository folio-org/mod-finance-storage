package org.folio.service.rollover;

import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.ERROR;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.IN_PROGRESS;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.SUCCESS;

import io.vertx.core.Promise;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import java.util.UUID;

import org.folio.dao.rollover.LedgerFiscalYearRolloverDAO;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus;
import org.folio.rest.persist.DBClient;
import org.folio.service.PostgresFunctionExecutionService;
import org.folio.service.budget.BudgetService;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class LedgerRolloverService {

  private static final Logger log = LoggerFactory.getLogger(LedgerRolloverService.class);

  private final LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO;
  private final BudgetService budgetService;
  private final RolloverProgressService rolloverProgressService;
  private final PostgresFunctionExecutionService postgresFunctionExecutionService;
  private final RestClient orderRolloverRestClient;

  public LedgerRolloverService(LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO, BudgetService budgetService,
      RolloverProgressService rolloverProgressService, PostgresFunctionExecutionService postgresFunctionExecutionService,
      RestClient orderRolloverRestClient) {
    this.ledgerFiscalYearRolloverDAO = ledgerFiscalYearRolloverDAO;
    this.budgetService = budgetService;
    this.rolloverProgressService = rolloverProgressService;
    this.postgresFunctionExecutionService = postgresFunctionExecutionService;
    this.orderRolloverRestClient = orderRolloverRestClient;
  }

  public Future<Void> rolloverLedger(LedgerFiscalYearRollover rollover, RequestContext requestContext) {

    if (rollover.getId() == null) {
      rollover.setId(UUID.randomUUID().toString());
    }

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress().withId(UUID.randomUUID()
      .toString())
      .withLedgerRolloverId(rollover.getId())
      .withBudgetsClosingRolloverStatus(SUCCESS)
      .withOverallRolloverStatus(IN_PROGRESS)
      .withMetadata(rollover.getMetadata());

    return rolloverPreparation(rollover, progress, requestContext)
      .onSuccess(aVoid -> startRollover(rollover, progress, requestContext));

  }

  public Future<Void> deleteRollover(String rolloverId, RequestContext requestContext) {
    DBClient client = requestContext.toDBClient();
    return rolloverProgressService.getLedgerRolloverProgressForRollover(rolloverId, client)
      .compose(rolloverProgress ->
        checkCanDeleteRollover(rolloverProgress)
        .compose(aVoid -> deleteRolloverWithProgress(rolloverProgress, requestContext)));
  }

  private  Future<Void> deleteRolloverWithProgress(LedgerFiscalYearRolloverProgress rolloverProgress, RequestContext requestContext) {
    DBClient client = requestContext.toDBClient();
    return client.startTx()
      .compose(aVoid -> rolloverProgressService.deleteRolloverProgress(rolloverProgress.getId(), client))
      .compose(aVoid -> ledgerFiscalYearRolloverDAO.delete(rolloverProgress.getLedgerRolloverId(), client))
      .compose(aVoid -> client.endTx())
      .onFailure(t -> {
        log.error("Rollover delete failed for Ledger {}", rolloverProgress.getLedgerRolloverId());
        client.rollbackTransaction();
    });
  }

  private Future<Void> checkCanDeleteRollover(LedgerFiscalYearRolloverProgress rolloverProgress) {
    Promise<Void> promise = Promise.promise();
    OverallRolloverStatus ordersRolloverStatus = rolloverProgress.getOrdersRolloverStatus();
    if (ordersRolloverStatus.equals(IN_PROGRESS)) {
      promise.fail(new HttpStatusException(422, "Can't delete in progress rollover"));
    } else {
      promise.complete();
    }
    return promise.future();
  }

  public Future<Void> rolloverPreparation(LedgerFiscalYearRollover rollover, LedgerFiscalYearRolloverProgress progress,
      RequestContext requestContext) {
    DBClient client = requestContext.toDBClient();
    return client.startTx()
      .compose(aVoid -> ledgerFiscalYearRolloverDAO.create(rollover, client))
      .compose(aVoid -> rolloverProgressService.createRolloverProgress(progress, client))
      .compose(aVoid -> closeBudgets(rollover, client))
      .compose(aVoid -> client.endTx())
      .onFailure(t -> {
        log.error("Rollover preparation failed for Ledger {}", t, rollover.getLedgerId());
        client.rollbackTransaction();
      });
  }

  public Future<Void> startRollover(LedgerFiscalYearRollover rollover, LedgerFiscalYearRolloverProgress progress,
      RequestContext requestContext) {
    DBClient client = requestContext.toDBClient();
    log.info("Rollover started for ledger {}", rollover.getLedgerId());

    return startFinancialRollover(rollover, progress, client)
      .compose(rolloverProgress -> startOrdersRollover(rollover, progress, requestContext))
      .onSuccess(aVoid -> log.info("Rollover completed for Ledger {}", rollover.getLedgerId()));
  }

  private Future<Void> closeBudgets(LedgerFiscalYearRollover rollover, DBClient client) {
    if (!Boolean.TRUE.equals(rollover.getNeedCloseBudgets())) {
      return Future.succeededFuture();
    }
    return budgetService.closeBudgets(rollover, client);
  }

  private Future<Void> startOrdersRollover(LedgerFiscalYearRollover rollover, LedgerFiscalYearRolloverProgress progress,
      RequestContext requestContext) {
    DBClient client = requestContext.toDBClient();
    log.info("Orders rollover started for Ledger {}", rollover.getLedgerId());
    return orderRolloverRestClient.postEmptyResponse(rollover, requestContext)
        .recover(t -> {
          log.error("Orders rollover failed for Ledger {}:", t, rollover.getLedgerId());
          return rolloverProgressService.updateRolloverProgress(progress.withOrdersRolloverStatus(ERROR)
            .withOverallRolloverStatus(ERROR), client)
            .compose(v -> Future.failedFuture(t));
        })
      .compose(aVoid -> rolloverProgressService.calculateAndUpdateOverallProgressStatus(progress.withOrdersRolloverStatus(SUCCESS), client));
  }

  private Future<Void> startFinancialRollover(LedgerFiscalYearRollover rollover, LedgerFiscalYearRolloverProgress progress,
      DBClient client) {
    return rolloverProgressService.updateRolloverProgress(progress.withFinancialRolloverStatus(IN_PROGRESS), client)
      .compose(aVoid -> runRolloverScript(rollover, client).recover(t -> rolloverProgressService
        .updateRolloverProgress(progress.withFinancialRolloverStatus(ERROR)
          .withOverallRolloverStatus(ERROR), client)
        .compose(v -> Future.failedFuture(t))))
      .compose(aVoid -> rolloverProgressService.calculateAndUpdateFinancialProgressStatus(progress.withOrdersRolloverStatus(IN_PROGRESS), client));
  }

  private Future<Void> runRolloverScript(LedgerFiscalYearRollover rollover, DBClient client) {
    return postgresFunctionExecutionService.runBudgetEncumbrancesRolloverScript(rollover, client)
      .onFailure(t -> log.error("Budget encumbrances rollover failed for Ledger {}:", t, rollover.getLedgerId()));
  }
}
