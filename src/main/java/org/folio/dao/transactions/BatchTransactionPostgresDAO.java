package org.folio.dao.transactions;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.interfaces.Results;

import java.util.Collections;
import java.util.List;

public class BatchTransactionPostgresDAO implements BatchTransactionDAO {
  private static final Logger logger = LogManager.getLogger();

  @Override
  public Future<List<Transaction>> getTransactionsByCriterion(Criterion criterion, DBConn conn) {
    logger.debug("Trying to get transactions by criterion: {}", criterion);
    return conn.get(TRANSACTIONS_TABLE, Transaction.class, criterion)
      .map(Results::getResults)
      .onSuccess(transactions -> logger.info("Successfully retrieved {} transactions", transactions.size()))
      .onFailure(e -> logger.error("Getting transactions by criterion failed, criterion: {}", criterion, e));
  }

  @Override
  public Future<List<Transaction>> getTransactionsByIds(List<String> ids, DBConn conn) {
    logger.debug("Trying to get transactions by ids = {}", ids);
    if (ids.isEmpty()) {
      return Future.succeededFuture(Collections.emptyList());
    }
    return getTransactionsByCriterion(buildCriterionByIds(ids), conn);
  }

  @Override
  public Future<Void> createTransactions(List<Transaction> transactions, DBConn conn) {
    return conn.saveBatch(TRANSACTIONS_TABLE, transactions)
      .mapEmpty();
  }

  @Override
  public Future<Void> updateTransactions(List<Transaction> transactions, DBConn conn) {
    return conn.updateBatch(TRANSACTIONS_TABLE, transactions)
      .mapEmpty();
  }

  @Override
  public Future<Void> deleteTransactionsByIds(List<String> ids, DBConn conn) {
    logger.debug("Trying to delete transactions by ids = {}", ids);
    if (ids.isEmpty()) {
      return Future.succeededFuture();
    }
    return conn.delete(TRANSACTIONS_TABLE, buildCriterionByIds(ids))
      .onSuccess(transactions -> logger.info("Successfully deleted {} transactions", ids.size()))
      .onFailure(e -> logger.error("Deleting transactions failed, ids: {}", ids, e))
      .mapEmpty();
  }

  private Criterion buildCriterionByIds(List<String> ids) {
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    ids.forEach(id -> criterionBuilder.with("id", id));
    return criterionBuilder.build();
  }
}
