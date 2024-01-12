package org.folio.dao.transactions;

import java.util.List;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import org.folio.rest.persist.DBConn;

public interface TransactionDAO {

  Future<List<Transaction>> getTransactions(Criterion criterion, DBConn conn);

  Future<List<Transaction>> getTransactions(List<String> ids, DBConn conn);

  Future<Integer> saveTransactionsToPermanentTable(String summaryId, DBConn conn);

  Future<Integer> saveTransactionsToPermanentTable(List<String> ids, DBConn conn);

  Future<Void> updatePermanentTransactions(List<Transaction> transactions, DBConn conn);

  Future<Void> deleteTransactions(Criterion build, DBConn conn);
}
