package org.folio.dao.transactions;

import static org.folio.rest.persist.PostgresClient.pojo2JsonObject;

import io.vertx.sqlclient.SqlResult;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.interfaces.Results;

import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.Response;

import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;

public abstract class BaseTemporaryTransactionsDAO implements TemporaryTransactionDAO {

  private static final Logger logger = LogManager.getLogger(BaseTemporaryTransactionsDAO.class);

  private final String tableName;

  protected BaseTemporaryTransactionsDAO(String tableName) {
    this.tableName = tableName;
  }

  @Override
  public Future<Transaction> createTempTransaction(Transaction transaction, String summaryId, String tenantId, DBConn conn) {
    logger.debug("Trying to create temp transaction");
    if (transaction.getId() == null) {
      transaction.setId(UUID.randomUUID().toString());
    }
    try {
      return conn.execute(createTempTransactionQuery(tenantId),
        Tuple.of(UUID.fromString(transaction.getId()), pojo2JsonObject(transaction)))
        .map(transaction)
        .onSuccess(x -> logger.info("New transaction with id {} successfully created", transaction.getId()))
        .onFailure(e -> logger.error("Transaction creation with id {} failed", transaction.getId(), e));
    } catch (Exception e) {
      logger.error("Creating new temp transaction with id {} failed", transaction.getId(), e);
      return Future.failedFuture(new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
    }
  }

  @Override
  public Future<List<Transaction>> getTempTransactions(Criterion criterion, DBConn conn) {
    logger.debug("Trying to get temp transactions by query: {}", criterion);
    return conn.get(tableName, Transaction.class, criterion, false)
      .map(Results::getResults)
      .onFailure(e -> logger.error("Failed to extract temporary transaction by criteria = {}", criterion, e));
  }

  @Override
  public Future<List<Transaction>> getTempTransactionsBySummaryId(String summaryId, DBConn conn) {
    logger.debug("Trying to temp transactions by summaryid {}", summaryId);
    return getTempTransactions(getSummaryIdCriteria(summaryId), conn);
  }

  public Future<Integer> deleteTempTransactions(String summaryId, DBConn conn) {
    logger.debug("Trying to delete temp transactions by summaryid {}", summaryId);
    Criterion criterion = getSummaryIdCriteria(summaryId);

    return conn.delete(getTableName(), criterion)
      .map(SqlResult::rowCount)
      .onSuccess(rowCount -> logger.info("Successfully deleted {} temp transactions by summaryid {}", rowCount, summaryId))
      .onFailure(e -> logger.error("Deleting temp transactions by summaryid {} failed", summaryId, e));
  }

  public String getTableName() {
    return tableName;
  }

  protected abstract String createTempTransactionQuery(String tenantId);

  protected abstract Criterion getSummaryIdCriteria(String summaryId);

}
