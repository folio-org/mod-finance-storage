package org.folio.service.rollover;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.dao.rollover.RolloverBudgetDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.RolloverBudgetExpenseClassTotalsService;

import java.util.List;

import static org.folio.rest.persist.HelperUtils.chainCall;


public class RolloverBudgetService {

  private static final Logger logger = LogManager.getLogger(RolloverBudgetService.class);

  private static final String LEDGER_ROLLOVER_ID = "ledgerRolloverId";

  private final RolloverBudgetDAO rolloverBudgetDAO;
  private final RolloverBudgetExpenseClassTotalsService rolloverBudgetExpenseClassTotalsService;

  public RolloverBudgetService(RolloverBudgetDAO rolloverBudgetDAO, RolloverBudgetExpenseClassTotalsService rolloverBudgetExpenseClassTotalsService) {
    this.rolloverBudgetDAO = rolloverBudgetDAO;
    this.rolloverBudgetExpenseClassTotalsService = rolloverBudgetExpenseClassTotalsService;
  }

  public Future<List<LedgerFiscalYearRolloverBudget>> getRolloverBudgets(String rolloverId, DBClient client) {
    Criterion filter = new CriterionBuilder().with(LEDGER_ROLLOVER_ID, rolloverId).build();
    return rolloverBudgetDAO.getRolloverBudgets(filter, client);
  }

  public Future<List<LedgerFiscalYearRolloverBudget>> updateBatch(List<LedgerFiscalYearRolloverBudget> budgets, DBClient client) {
    return rolloverBudgetDAO.updateBatch(budgets, client);
  }

  public Future<Void> deleteRolloverBudgets(String ledgerRolloverId, DBClient client) {
    Criterion criterion = new CriterionBuilder().with(LEDGER_ROLLOVER_ID, ledgerRolloverId).build();
    return rolloverBudgetDAO.deleteByQuery(criterion, client);
  }

  public Future<Void> updateRolloverBudgetsExpenseClassTotals(List<LedgerFiscalYearRolloverBudget> budgets, DBClient dbClient) {
    Promise<Void> promise = Promise.promise();
    retrieveAssociatedDataAndUpdateExpenseClassTotals(budgets, dbClient)
      .onComplete(result -> {
        if (result.failed()) {
          logger.error("updateRolloverBudgetsExpenseClassTotals:: Transactions or expense class data failed to be processed", result.cause());
          promise.fail("Error when updating rollover budget expense class totals");
        } else {
          logger.info("updateRolloverBudgetsExpenseClassTotals:: Transactions or expense class data were successfully processed");
          promise.complete();
        }
      });
    return promise.future();
  }

  private Future<LedgerFiscalYearRolloverBudget> retrieveAssociatedDataAndUpdateExpenseClassTotals(List<LedgerFiscalYearRolloverBudget> budgets, DBClient dbClient) {
    return chainCall(budgets, budget -> rolloverBudgetExpenseClassTotalsService.getBudgetWithUpdatedExpenseClassTotals(budget, dbClient)
      .compose(budgetWithUpdatedTotals -> rolloverBudgetDAO.updateRolloverBudget(budget, dbClient)));
  }

}
