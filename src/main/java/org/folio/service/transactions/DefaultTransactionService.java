package org.folio.service.transactions;

import io.vertx.core.Future;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;

public class DefaultTransactionService extends AbstractTransactionService {

  public DefaultTransactionService(TransactionDAO transactionDAO) {
    super(transactionDAO);
  }

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, DBConn conn) {
    return transactionDAO.createTransaction(transaction, conn);
  }
}
