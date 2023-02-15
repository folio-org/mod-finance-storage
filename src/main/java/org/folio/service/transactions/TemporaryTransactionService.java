package org.folio.service.transactions;

import io.vertx.core.Future;
import org.folio.dao.transactions.TemporaryTransactionDAO;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;

import java.util.List;

public class TemporaryTransactionService {

  private final TemporaryTransactionDAO temporaryTransactionDAO;

  public TemporaryTransactionService(TemporaryTransactionDAO temporaryTransactionDAO) {
    this.temporaryTransactionDAO = temporaryTransactionDAO;
  }

  public Future<List<Transaction>> getTransactions(LedgerFiscalYearRolloverBudget budget, DBClient client) {
    CriterionBuilder criterionBuilder = new CriterionBuilder("AND");
    criterionBuilder.withJson("toFundId", "=", budget.getFundId())
      .withJson("fiscalYearId", "=", budget.getFiscalYearId())
      .withOperation("OR")
      .withJson("fiscalYearId", "=", budget.getFiscalYearId())
      .withOperation("AND")
      .withJson("fromFundId", "=", budget.getFundId());

    return client.getPgClient()
      .withTrans(conn -> temporaryTransactionDAO.getTempTransactions(criterionBuilder.build(), conn));
  }

}
