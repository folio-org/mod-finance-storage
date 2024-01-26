package org.folio.service.transactions;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;

public interface TransactionService {

  Future<Transaction> createTransaction(Transaction transaction, DBConn conn);

  Future<Void> updateTransaction(Transaction transaction, DBConn conn);

  Future<Void> deleteTransactionById(String id, DBConn conn);
}
