package org.folio.service.transactions;

import io.vertx.core.Future;
import org.folio.dao.transactions.TemporaryEncumbranceDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;

import java.util.List;

public class TemporaryEncumbranceService {

  private final TemporaryEncumbranceDAO temporaryEncumbranceDAO;

  public TemporaryEncumbranceService(TemporaryEncumbranceDAO temporaryEncumbranceDAO) {
    this.temporaryEncumbranceDAO = temporaryEncumbranceDAO;
  }

  public Future<List<Transaction>> getTransactions(LedgerFiscalYearRolloverBudget budget, DBConn conn) {
    CriterionBuilder criterionBuilder = new CriterionBuilder("AND");
    criterionBuilder.withJson("toFundId", "=", budget.getFundId())
      .withJson("fiscalYearId", "=", budget.getFiscalYearId())
      .withOperation("OR")
      .withJson("fiscalYearId", "=", budget.getFiscalYearId())
      .withOperation("AND")
      .withJson("fromFundId", "=", budget.getFundId());

    return temporaryEncumbranceDAO.getTempTransactions(criterionBuilder.build(), conn);
  }

}
