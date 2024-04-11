package org.folio.service.rollover;

import static org.folio.dao.budget.BudgetExpenseClassDAOImpl.TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE;
import static org.folio.dao.transactions.TemporaryEncumbrancePostgresDAO.TEMPORARY_ENCUMBRANCE_TRANSACTIONS_TABLE;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError.ErrorType.FINANCIAL_ROLLOVER;
import static org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError.ErrorType.ORDER_ROLLOVER;
import static org.folio.rest.jaxrs.model.RolloverStatus.ERROR;
import static org.folio.rest.jaxrs.model.RolloverStatus.IN_PROGRESS;
import static org.folio.rest.jaxrs.model.RolloverStatus.SUCCESS;

import java.util.List;
import java.util.UUID;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.rollover.LedgerFiscalYearRolloverDAO;
import org.folio.rest.core.RestClient;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError.ErrorType;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.jaxrs.model.RolloverStatus;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;
import org.folio.service.PostgresFunctionExecutionService;
import org.folio.service.budget.BudgetService;
import org.folio.service.email.EmailService;
import org.folio.service.fiscalyear.FiscalYearService;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.HttpException;
import org.folio.utils.CalculationUtils;

public class LedgerRolloverService {

  private static final Logger logger = LogManager.getLogger(LedgerRolloverService.class);

  private final LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO;
  private final BudgetService budgetService;
  private final FiscalYearService fiscalYearService;
  private final RolloverProgressService rolloverProgressService;
  private final RolloverErrorService rolloverErrorService;
  private final RolloverBudgetService rolloverBudgetService;
  private final PostgresFunctionExecutionService postgresFunctionExecutionService;
  private final RolloverValidationService rolloverValidationService;
  private final RestClient orderRolloverRestClient;
  private final EmailService emailService;

  public LedgerRolloverService(FiscalYearService fiscalYearService, LedgerFiscalYearRolloverDAO ledgerFiscalYearRolloverDAO,
      BudgetService budgetService, RolloverProgressService rolloverProgressService, RolloverErrorService rolloverErrorService,
      RolloverBudgetService rolloverBudgetService,PostgresFunctionExecutionService postgresFunctionExecutionService,
      RolloverValidationService rolloverValidationService, RestClient orderRolloverRestClient, EmailService emailService) {
    this.fiscalYearService = fiscalYearService;
    this.ledgerFiscalYearRolloverDAO = ledgerFiscalYearRolloverDAO;
    this.budgetService = budgetService;
    this.rolloverProgressService = rolloverProgressService;
    this.rolloverErrorService = rolloverErrorService;
    this.rolloverBudgetService = rolloverBudgetService;
    this.postgresFunctionExecutionService = postgresFunctionExecutionService;
    this.rolloverValidationService = rolloverValidationService;
    this.orderRolloverRestClient = orderRolloverRestClient;
    this.emailService = emailService;
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

    DBClient client = requestContext.toDBClient();
    return client.withTrans(conn -> rolloverPreparation(rollover, progress, conn))
      .compose(v -> client.withConn(conn -> startRollover(rollover, progress, requestContext, conn)));
  }

  public Future<Void> deleteRollover(String rolloverId, RequestContext requestContext) {
    DBClient client = requestContext.toDBClient();
    return client.withTrans(conn -> rolloverProgressService.getLedgerRolloverProgressForRollover(rolloverId, conn)
      .compose(rolloverProgress ->
        checkCanDeleteRollover(rolloverProgress)
        .compose(aVoid -> deleteRolloverWithProgressAndErrors(rolloverProgress, conn)))
    );
  }

  private  Future<Void> deleteRolloverWithProgressAndErrors(LedgerFiscalYearRolloverProgress rolloverProgress, DBConn conn) {
    return rolloverProgressService.deleteRolloverProgress(rolloverProgress.getId(), conn)
      .compose(aVoid -> rolloverErrorService.deleteRolloverErrors(rolloverProgress.getLedgerRolloverId(), conn))
      .compose(aVoid -> rolloverBudgetService.deleteRolloverBudgets(rolloverProgress.getLedgerRolloverId(), conn))
      .compose(aVoid -> ledgerFiscalYearRolloverDAO.delete(rolloverProgress.getLedgerRolloverId(), conn))
      .onFailure(t -> logger.error("deleteRolloverWithProgressAndErrors:: Rollover delete failed for Ledger {}",
        rolloverProgress.getLedgerRolloverId(), t));
  }

  private Future<Void> checkCanDeleteRollover(LedgerFiscalYearRolloverProgress rolloverProgress) {
    Promise<Void> promise = Promise.promise();
    RolloverStatus ordersRolloverStatus = rolloverProgress.getOrdersRolloverStatus();
    if (ordersRolloverStatus.equals(IN_PROGRESS)) {
      logger.error("checkCanDeleteRollover:: Can't delete in progress rollover with id {}", rolloverProgress.getId());
      promise.fail(new HttpException(422, "Can't delete in progress rollover"));
    } else {
      promise.complete();
    }
    return promise.future();
  }

  public Future<Void> rolloverPreparation(LedgerFiscalYearRollover rollover, LedgerFiscalYearRolloverProgress progress,
      DBConn conn) {
    return rolloverValidationService.checkRolloverExists(rollover, conn)
      .compose(aVoid -> ledgerFiscalYearRolloverDAO.create(rollover, conn))
      .compose(aVoid -> rolloverProgressService.createRolloverProgress(progress, conn))
      .compose(aVoid -> fiscalYearService.populateRolloverWithCurrencyFactor(rollover, conn))
      .compose(aVoid -> closeBudgets(rollover, conn))
      .onFailure(t -> logger.error("rolloverPreparation:: Rollover preparation failed for Ledger {}",
        rollover.getLedgerId(), t));
  }

  public Future<Void> startRollover(LedgerFiscalYearRollover rollover, LedgerFiscalYearRolloverProgress progress,
      RequestContext requestContext, DBConn conn) {
    logger.info("startRollover:: Rollover started for ledger {}", rollover.getLedgerId());

    return startFinancialRollover(rollover, progress, conn)
      .compose(rolloverProgress -> startOrdersRollover(rollover, progress, requestContext, conn))
      .compose(aVoid -> emailService.createAndSendEmail(requestContext, rollover, conn)
        .recover(t -> {
          logger.warn("Ignoring error when sending an email after the rollover starts", t);
          return Future.succeededFuture(null);
        }))
      .onSuccess(aVoid -> logger.info("startRollover:: Rollover completed for Ledger {}", rollover.getLedgerId()));
  }

  private Future<Void> closeBudgets(LedgerFiscalYearRollover rollover, DBConn conn) {
    if (LedgerFiscalYearRollover.RolloverType.PREVIEW.equals(rollover.getRolloverType()) || !Boolean.TRUE.equals(rollover.getNeedCloseBudgets())) {
      logger.info("closeBudgets:: Close budgets skipped for Ledger {} and rollover type {} and needCloseBudgets is {}",
        rollover.getLedgerId(), rollover.getRolloverType(), rollover.getNeedCloseBudgets());
      return Future.succeededFuture();
    }
    return budgetService.closeBudgets(rollover, conn);
  }

  private Future<Void> startOrdersRollover(LedgerFiscalYearRollover rollover, LedgerFiscalYearRolloverProgress progress,
      RequestContext requestContext, DBConn conn) {
    if (LedgerFiscalYearRollover.RolloverType.PREVIEW.equals(rollover.getRolloverType()) || rollover.getEncumbrancesRollover().isEmpty()) {
      logger.info("startOrdersRollover:: Orders rollover skipped for Ledger {} and rollover type {}", rollover.getLedgerId(), rollover.getRolloverType());
      return rolloverProgressService.calculateAndUpdateOverallProgressStatus(progress.withOrdersRolloverStatus(SUCCESS), conn);
    }
    logger.info("startOrdersRollover:: Orders rollover started for Ledger {}", rollover.getLedgerId());
    return orderRolloverRestClient.postEmptyResponse(rollover, requestContext)
      .recover(t -> handleOrderRolloverError(t, rollover, progress, conn));
  }

  private Future<Void> startFinancialRollover(LedgerFiscalYearRollover rollover, LedgerFiscalYearRolloverProgress progress,
      DBConn conn) {
    return rolloverProgressService.updateRolloverProgress(progress.withFinancialRolloverStatus(IN_PROGRESS), conn)
      .compose(aVoid -> runRolloverScript(rollover, conn))
      .recover(t -> handleFinancialRolloverError(t, rollover, progress, conn))
      .compose(aVoid -> updateRolloverBudgetsWithCalculatedAmounts(rollover.getId(), conn))
      .compose(aVoid -> updateRolloverBudgetsWithExpenseClassTotals(rollover.getId(), conn))
      .compose(aVoid -> dropRolloverTemporaryTables(conn))
      .compose(aVoid -> rolloverProgressService.calculateAndUpdateFinancialProgressStatus(progress.withOrdersRolloverStatus(IN_PROGRESS), conn));
  }

  private Future<Void> runRolloverScript(LedgerFiscalYearRollover rollover, DBConn conn) {
    return postgresFunctionExecutionService.runBudgetEncumbrancesRolloverScript(rollover, conn)
      .onFailure(t -> logger.error("runRolloverScript:: Budget encumbrances rollover failed for Ledger {}:",
        rollover.getLedgerId(), t));
  }

  private Future<List<LedgerFiscalYearRolloverBudget>> updateRolloverBudgetsWithCalculatedAmounts(String rolloverId, DBConn conn) {
    return rolloverBudgetService.getRolloverBudgets(rolloverId, conn)
      .compose(budgets -> {
        budgets.forEach(CalculationUtils::calculateBudgetSummaryFields);
        return rolloverBudgetService.updateBatch(budgets, conn);
      });
  }

  private Future<Void> updateRolloverBudgetsWithExpenseClassTotals(String rolloverId, DBConn conn) {
    return rolloverBudgetService.getRolloverBudgets(rolloverId, conn)
      .compose(budgets -> rolloverBudgetService.updateRolloverBudgetsExpenseClassTotals(budgets, conn));
  }

  private Future<Void> dropRolloverTemporaryTables(DBConn conn) {
    return postgresFunctionExecutionService.dropTable(TEMPORARY_ENCUMBRANCE_TRANSACTIONS_TABLE, true, conn)
      .compose(aVoid -> postgresFunctionExecutionService.dropTable(TEMPORARY_BUDGET_EXPENSE_CLASS_TABLE, true, conn));
  }

  private Future<Void> handleOrderRolloverError(Throwable t, LedgerFiscalYearRollover rollover,
      LedgerFiscalYearRolloverProgress progress, DBConn conn) {
    logger.error("handleOrderRolloverError:: Orders rollover failed for ledger {}", rollover.getLedgerId(), t);
    return saveRolloverError(rollover.getId(), t, ORDER_ROLLOVER, "Overall order rollover", conn)
      .compose(v -> rolloverProgressService.updateRolloverProgress(
        progress.withOrdersRolloverStatus(ERROR).withOverallRolloverStatus(ERROR), conn))
      .compose(v -> Future.failedFuture(t));
  }

  private Future<Void> handleFinancialRolloverError(Throwable t, LedgerFiscalYearRollover rollover,
      LedgerFiscalYearRolloverProgress progress, DBConn conn) {
    logger.error("handleFinancialRolloverError:: Financial rollover failed for ledger {}", rollover.getLedgerId(), t);
    return saveRolloverError(rollover.getId(), t, FINANCIAL_ROLLOVER, "Overall financial rollover", conn)
      .compose(v -> rolloverProgressService.updateRolloverProgress(
        progress.withFinancialRolloverStatus(ERROR).withOverallRolloverStatus(ERROR), conn))
      .compose(v -> Future.failedFuture(t));
  }

  private Future<Void> saveRolloverError(String rolloverId, Throwable t, ErrorType errorType,
      String failedAction, DBConn conn) {
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
    return rolloverErrorService.createRolloverError(error, conn);
  }
}
