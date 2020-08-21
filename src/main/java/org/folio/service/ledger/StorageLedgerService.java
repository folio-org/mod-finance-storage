package org.folio.service.ledger;

import org.folio.dao.ledger.LedgerDAO;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.fund.FundService;

import io.vertx.core.Future;

public class StorageLedgerService implements LedgerService {

  private LedgerDAO ledgerDAO;
  private FundService fundService;

  public StorageLedgerService(LedgerDAO ledgerDAO, FundService fundService) {
    this.ledgerDAO = ledgerDAO;
    this.fundService = fundService;
  }

  public Future<Ledger> getLedgerByTransaction(Transaction transaction, DBClient dbClient) {
    String fundId = transaction.getTransactionType() == Transaction.TransactionType.CREDIT ? transaction.getToFundId() : transaction.getFromFundId();
    return fundService.getFundById(fundId, dbClient)
      .compose(fund -> ledgerDAO.getLedgerById(fund.getLedgerId(), dbClient));
  }

}
