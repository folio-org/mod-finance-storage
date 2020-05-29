package org.folio.service.ledger;

import java.util.Map;

import org.folio.dao.ledger.LedgerDAO;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.fund.FundService;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class StorageLedgerService implements LedgerService {

  private LedgerDAO ledgerDAO;
  private FundService fundService;

  public StorageLedgerService(LedgerDAO ledgerDAO, FundService fundService) {
    this.ledgerDAO = ledgerDAO;
    this.fundService = fundService;
  }

  public Future<Ledger> getLedgerByTransaction(Transaction transaction, Context context, Map<String, String> headers) {
    String fundId = transaction.getTransactionType() == Transaction.TransactionType.CREDIT ? transaction.getToFundId() : transaction.getFromFundId();
    DBClient client = new DBClient(context, headers);
    return fundService.getFundById(fundId, context, headers)
      .compose(fund -> ledgerDAO.getLedgerById(fund.getLedgerId(), client));
  }

}
