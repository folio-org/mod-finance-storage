package org.folio.dao.transactions;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBConn;

import java.util.List;

public interface BatchTransactionDAO {
  Future<List<Transaction>> getTransactionsByCriterion(Criterion criterion, DBConn conn);
  Future<List<Transaction>> getTransactionsByIds(List<String> ids, DBConn conn);
  Future<Void> createTransactions(List<Transaction> transactions, DBConn conn);
  Future<Void> updateTransactions(List<Transaction> transactions, DBConn conn);
  Future<Void> deleteTransactionsByIds(List<String> ids, DBConn conn);
}
