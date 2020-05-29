package org.folio.service.ledgerfy;

import java.util.List;

import org.folio.dao.ledgerfy.LedgerFiscalYearDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.LedgerFY;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.service.fund.FundService;

import io.vertx.core.Future;

public class StorageLedgerFiscalYearService implements LedgerFiscalYearService {

  private LedgerFiscalYearDAO ledgerFiscalYearDAO;
  private FundService fundService;

  public StorageLedgerFiscalYearService(LedgerFiscalYearDAO ledgerFiscalYearDAO, FundService fundService) {
    this.ledgerFiscalYearDAO = ledgerFiscalYearDAO;
    this.fundService = fundService;
  }

  public Future<List<LedgerFY>> getLedgerFiscalYearsByBudgets(List<Budget> budgets, DBClient client) {
    return fundService.getFundsByBudgets(budgets, client)
        .compose(funds -> {
          CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
          funds.stream().map(Fund::getLedgerId)
            .forEach(id -> criterionBuilder.with("ledgerId", id));
          criterionBuilder.withOperation("AND");
          criterionBuilder.with("fiscalYearId", budgets.get(0).getFiscalYearId());
          return ledgerFiscalYearDAO.getLedgerFiscalYears(criterionBuilder.build(), client);
        });
  }

  @Override
  public Future<Void> updateLedgerFiscalYears(List<LedgerFY> ledgersFYears, DBClient client) {
    if (ledgersFYears.isEmpty()) {
      return Future.succeededFuture();
    } else {
      return ledgerFiscalYearDAO.updateLedgerFiscalYears(ledgersFYears, client);
    }
  }

}
