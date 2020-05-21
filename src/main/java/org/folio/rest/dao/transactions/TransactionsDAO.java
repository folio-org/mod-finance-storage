package org.folio.rest.dao.transactions;

import java.util.List;

import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;

import io.vertx.core.Future;

public interface TransactionsDAO {

  Future<List<Transaction>> getTransactions(Criterion criterion, DBClient client);

  Future<Integer> saveTransactionsToPermanentTable(String summaryId, DBClient client);

  Future<Void> updatePermanentTransactions(List<Transaction> transactions, DBClient client);
}
