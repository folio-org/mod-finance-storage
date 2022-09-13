package org.folio.service.rollover;

import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError.ErrorType.FINANCIAL_ROLLOVER;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError.ErrorType.ORDER_ROLLOVER;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.ERROR;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.IN_PROGRESS;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus.SUCCESS;

import java.util.List;
import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.rollover.LedgerFiscalYearRolloverDAO;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover.RolloverType;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError.ErrorType;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress.OverallRolloverStatus;
import org.folio.rest.persist.DBClient;
import org.folio.service.PostgresFunctionExecutionService;
import org.folio.service.budget.BudgetService;
import org.folio.service.fiscalyear.FiscalYearService;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;
import org.folio.utils.CalculationUtils;

public class LedgerRolloverService {

  private static final Logger log = LogManager.getLogger(LedgerRolloverService.class);

  private final LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO;
  private final BudgetService budgetService;
  private final FiscalYearService fiscalYearService;
  private final RolloverProgressService rolloverProgressService;
  private final RolloverErrorService rolloverErrorService;
  private final RolloverBudgetService rolloverBudgetService;
  private final PostgresFunctionExecutionService postgresFunctionExecutionService;
  private final RolloverValidationService rolloverValidationService;
  private final RestClient orderRolloverRestClient;

  public LedgerRolloverService(FiscalYearService fiscalYearService, LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO,
      BudgetService budgetService, RolloverProgressService rolloverProgressService, RolloverErrorService rolloverErrorService,
      RolloverBudgetService rolloverBudgetService,PostgresFunctionExecutionService postgresFunctionExecutionService,
      RolloverValidationService rolloverValidationService, RestClient orderRolloverRestClient) {
    this.fiscalYearService = fiscalYearService;
    this.ledgerFiscalYearRolloverDAO = ledgerFiscalYearRolloverDAO;
    this.budgetService = budgetService;
    this.rolloverProgressService = rolloverProgressService;
    this.rolloverErrorService = rolloverErrorService;
    this.rolloverBudgetService = rolloverBudgetService;
    this.postgresFunctionExecutionService = postgresFunctionExecutionService;
    this.rolloverValidationService = rolloverValidationService;
    this.orderRolloverRestClient = orderRolloverRestClient;
  }

  public Future<Void> rolloverLedger(LedgerFiscalYearRollover rollover, RequestContext requestContext) {

    if (rollover.getId() == null) {
      rollover.setId(UUID.randomUUID().toString());
    }

    LedgerFiscalYearRolloverProgress progress = new LedgerFiscalYearRolloverProgress()
      .withId(UUID.randomUUID().toString())
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
        .compose(aVoid -> deleteRolloverWithProgressAndErrors(rolloverProgress, requestContext)));
  }

  private  Future<Void> deleteRolloverWithProgressAndErrors(LedgerFiscalYearRolloverProgress rolloverProgress, RequestContext requestContext) {
    DBClient client = requestContext.toDBClient();
    return client.startTx()
      .compose(aVoid -> rolloverProgressService.deleteRolloverProgress(rolloverProgress.getId(), client))
      .compose(aVoid -> rolloverErrorService.deleteRolloverErrors(rolloverProgress.getLedgerRolloverId(), client))
      .compose(aVoid -> rolloverBudgetService.deleteRolloverBudgets(rolloverProgress.getLedgerRolloverId(), client))
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
      promise.fail(new HttpException(422, "Can't delete in progress rollover"));
    } else {
      promise.complete();
    }
    return promise.future();
  }

  public Future<Void> rolloverPreparation(LedgerFiscalYearRollover rollover, LedgerFiscalYearRolloverProgress progress,
      RequestContext requestContext) {
    DBClient client = requestContext.toDBClient();
    return client.startTx()
      .compose(aVoid -> rolloverValidationService.checkRolloverExists(rollover, client))
      .compose(aVoid -> ledgerFiscalYearRolloverDAO.create(rollover, client))
      .compose(aVoid -> rolloverProgressService.createRolloverProgress(progress, client))
      .compose(aVoid -> fiscalYearService.populateRolloverWithCurrencyFactor(rollover, requestContext))
      .compose(aVoid -> closeBudgets(rollover, client))
      .compose(aVoid -> client.endTx())
      .onFailure(t -> {
        log.error("Rollover preparation failed for Ledger {}", rollover.getLedgerId(), t);
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
    if (RolloverType.PREVIEW.equals(rollover.getRolloverType()) || !Boolean.TRUE.equals(rollover.getNeedCloseBudgets())) {
      log.info("Close budgets skipped for Ledger {} and rollover type {} and needCloseBudgets is {}",
        rollover.getLedgerId(), rollover.getRolloverType(), rollover.getNeedCloseBudgets());
      return Future.succeededFuture();
    }
    return budgetService.closeBudgets(rollover, client);
  }

  private Future<Void> startOrdersRollover(LedgerFiscalYearRollover rollover, LedgerFiscalYearRolloverProgress progress,
      RequestContext requestContext) {
    DBClient client = requestContext.toDBClient();
    if (RolloverType.PREVIEW.equals(rollover.getRolloverType()) || rollover.getEncumbrancesRollover().isEmpty()) {
      log.info("Orders rollover skipped for Ledger {} and rollover type {}", rollover.getLedgerId(), rollover.getRolloverType());
      return rolloverProgressService.calculateAndUpdateOverallProgressStatus(progress.withOrdersRolloverStatus(SUCCESS), client);
    }
    log.info("Orders rollover started for Ledger {}", rollover.getLedgerId());
    return orderRolloverRestClient.postEmptyResponse(rollover, requestContext)
      .recover(t -> handleOrderRolloverError(t, rollover, progress, client))
      .compose(aVoid -> rolloverProgressService.calculateAndUpdateOverallProgressStatus(
        progress.withOrdersRolloverStatus(SUCCESS), client));
  }

  private Future<Void> startFinancialRollover(LedgerFiscalYearRollover rollover, LedgerFiscalYearRolloverProgress progress,
      DBClient client) {
    return rolloverProgressService.updateRolloverProgress(progress.withFinancialRolloverStatus(IN_PROGRESS), client)
      .compose(aVoid -> runRolloverScript(rollover, client))
      .recover(t -> handleFinancialRolloverError(t, rollover, progress, client))
      .compose(aVoid -> updateRolloverBudgetsWithCalculatedAmounts(rollover.getId(), client))
      .compose(aVoid -> rolloverProgressService.calculateAndUpdateFinancialProgressStatus(progress.withOrdersRolloverStatus(IN_PROGRESS), client));
  }

  private Future<Void> runRolloverScript(LedgerFiscalYearRollover rollover, DBClient client) {
    return postgresFunctionExecutionService.runBudgetEncumbrancesRolloverScript(rollover, client)
      .onFailure(t -> log.error("Budget encumbrances rollover failed for Ledger {}:", rollover.getLedgerId(), t));
  }

  private Future<List<LedgerFiscalYearRolloverBudget>> updateRolloverBudgetsWithCalculatedAmounts(String rolloverId, DBClient dbClient) {
    return rolloverBudgetService.getRolloverBudgets(rolloverId, dbClient)
      .compose(budgets -> {
        budgets.forEach(CalculationUtils::calculateBudgetSummaryFields);
        return rolloverBudgetService.updateBatch(budgets, dbClient);
      });
  }

  private Future<Void> handleOrderRolloverError(Throwable t, LedgerFiscalYearRollover rollover,
      LedgerFiscalYearRolloverProgress progress, DBClient client) {
    log.error("Orders rollover failed for ledger {}", rollover.getLedgerId(), t);
    return saveRolloverError(rollover.getId(), t, ORDER_ROLLOVER, "Overall order rollover", client)
      .compose(v -> rolloverProgressService.updateRolloverProgress(
        progress.withOrdersRolloverStatus(ERROR).withOverallRolloverStatus(ERROR), client))
      .compose(v -> Future.failedFuture(t));
  }

  private Future<Void> handleFinancialRolloverError(Throwable t, LedgerFiscalYearRollover rollover,
      LedgerFiscalYearRolloverProgress progress, DBClient client) {
    log.error("Financial rollover failed for ledger {}", rollover.getLedgerId(), t);
    return saveRolloverError(rollover.getId(), t, FINANCIAL_ROLLOVER, "Overall financial rollover", client)
      .compose(v -> rolloverProgressService.updateRolloverProgress(
        progress.withFinancialRolloverStatus(ERROR).withOverallRolloverStatus(ERROR), client))
      .compose(v -> Future.failedFuture(t));
  }

  private Future<Void> saveRolloverError(String rolloverId, Throwable t, ErrorType errorType,
      String failedAction, DBClient client) {
    String message;
    try {
      JsonObject errorObj = new JsonObject(t.getMessage());
      message = errorObj.getJsonArray("errors").getJsonObject(0).getString("message");
      if (message == null)
        message = t.getMessage();
    } catch (Exception ex) {
      message = t.getMessage();
    }
    LedgerFiscalYearRolloverError error = new LedgerFiscalYearRolloverError()
      .withLedgerRolloverId(rolloverId)
      .withErrorType(errorType)
      .withFailedAction(failedAction)
      .withErrorMessage(message);
    return client.startTx()
      .compose(v -> rolloverErrorService.createRolloverError(error, client))
      .compose(v -> client.endTx());
  }
}
