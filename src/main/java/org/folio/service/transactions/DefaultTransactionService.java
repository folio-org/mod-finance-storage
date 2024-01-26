package org.folio.service.transactions;

import org.folio.dao.transactions.TransactionDAO;

public class DefaultTransactionService extends AbstractTransactionService {

  public DefaultTransactionService(TransactionDAO transactionDAO) {
    super(transactionDAO);
  }

}
