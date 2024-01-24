package org.folio.service.transactions;

import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;

import io.vertx.core.Future;

public abstract class AbstractTransactionService implements TransactionService {

  protected final TransactionDAO transactionDAO;

  public static final String TRANSACTION_TABLE = "transaction";
  public static final String FROM_FUND_ID = "fromFundId";
  public static final String TO_FUND_ID = "toFundId";

  AbstractTransactionService(TransactionDAO transactionDAO) {
    this.transactionDAO = transactionDAO;
  }

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, DBConn conn) {
    return transactionDAO.createTransaction(transaction, conn);
  }

  @Override
  public Future<Void> updateTransaction(Transaction transaction, DBConn conn) {
    return transactionDAO.updateTransaction(transaction, conn);
  }

  @Override
  public Future<Void> deleteTransactionById(String id, DBConn conn) {
    return transactionDAO.deleteTransactionById(id, conn);
  }

}
