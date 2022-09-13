package org.folio.service.rollover;

import io.vertx.core.Future;
import org.folio.dao.rollover.RolloverBudgetDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;

import java.util.List;


public class RolloverBudgetService {
  private static final String LEDGER_ROLLOVER_ID = "ledgerRolloverId";

  private final RolloverBudgetDAO rolloverBudgetDAO;

  public RolloverBudgetService(RolloverBudgetDAO rolloverBudgetDAO) {
    this.rolloverBudgetDAO = rolloverBudgetDAO;
  }

  public Future<List<LedgerFiscalYearRolloverBudget>> getRolloverBudgets(String rolloverId, DBClient client) {
    return rolloverBudgetDAO.getRolloverBudgets(rolloverId, client);
  }

  public Future<List<LedgerFiscalYearRolloverBudget>> updateBatch(List<LedgerFiscalYearRolloverBudget> budgets, DBClient client) {
    return rolloverBudgetDAO.updateBatch(budgets, client);
  }

  public Future<Void> deleteRolloverBudgets(String ledgerRolloverId, DBClient client) {
    Criterion criterion = new CriterionBuilder().with(LEDGER_ROLLOVER_ID, ledgerRolloverId).build();
    return rolloverBudgetDAO.deleteByQuery(criterion, client);
  }
}
