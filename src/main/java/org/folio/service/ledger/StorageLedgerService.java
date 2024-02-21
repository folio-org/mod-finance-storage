package org.folio.service.ledger;

import org.folio.dao.ledger.LedgerDAO;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;
import org.folio.service.fund.FundService;

import io.vertx.core.Future;

import java.util.List;

public class StorageLedgerService implements LedgerService {

  private final LedgerDAO ledgerDAO;
  private final FundService fundService;

  public StorageLedgerService(LedgerDAO ledgerDAO, FundService fundService) {
    this.ledgerDAO = ledgerDAO;
    this.fundService = fundService;
  }

  @Override
  public Future<Ledger> getLedgerByTransaction(Transaction transaction, DBConn conn) {
    String fundId = transaction.getTransactionType() == Transaction.TransactionType.CREDIT ? transaction.getToFundId() : transaction.getFromFundId();
    return fundService.getFundById(fundId, conn)
      .compose(fund -> ledgerDAO.getLedgerById(fund.getLedgerId(), conn));
  }

  @Override
  public Future<List<Ledger>> getLedgersByIds(List<String> ids, DBConn conn) {
    return ledgerDAO.getLedgersByIds(ids, conn);
  }
}
