package org.folio.dao.transactions;

import java.util.List;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;

public interface TransactionDAO {

  Future<List<Transaction>> getTransactions(Criterion criterion, DBClient client);

  Future<List<Transaction>> getTransactions(List<String> ids, DBClient client);

  Future<Integer> saveTransactionsToPermanentTable(String summaryId, DBClient client);

  Future<Integer> saveTransactionsToPermanentTable(List<String> ids, DBClient client);

  Future<Void> updatePermanentTransactions(List<Transaction> transactions, DBClient client);

  Future<Void> deleteTransactions(Criterion build, DBClient client);
}
