package org.folio.dao.transactions;

import static org.folio.rest.persist.PostgresClient.pojo2JsonObject;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.util.ResponseUtils;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.Response;

import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Tuple;

public abstract class BaseTemporaryTransactionsDAO implements TemporaryTransactionDAO {

  protected final Logger logger = LogManager.getLogger(this.getClass());

  private final String tableName;

  protected BaseTemporaryTransactionsDAO(String tableName) {
    this.tableName = tableName;
  }

  @Override
  public Future<Transaction> createTempTransaction(Transaction transaction, String summaryId, String tenantId, Conn conn) {
    if (transaction.getId() == null) {
      transaction.setId(UUID.randomUUID().toString());
    }

    logger.debug("Creating new transaction with id={}", transaction.getId());

    try {
      return conn.execute(createTempTransactionQuery(tenantId),
        Tuple.of(UUID.fromString(transaction.getId()), pojo2JsonObject(transaction)))
        .map(transaction)
        .recover(ResponseUtils::handleFailure)
        .onSuccess(x -> logger.debug("New transaction with id={} successfully created", transaction.getId()))
        .onFailure(e -> logger.error("Transaction creation with id={} failed", transaction.getId(), e));
    } catch (Exception e) {
      return Future.failedFuture(new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
    }
  }

  public Future<List<Transaction>> getTempTransactions(Criterion criterion, Conn conn) {
    return conn.get(tableName, Transaction.class, criterion, false)
      .map(Results::getResults)
      .onFailure(e -> logger.error("Failed to extract temporary transaction by criteria = {}", criterion, e))
      .recover(ResponseUtils::handleFailure);
  }

  @Override
  public Future<List<Transaction>> getTempTransactionsBySummaryId(String summaryId, Conn conn) {
    return getTempTransactions(getSummaryIdCriteria(summaryId), conn);
  }

  public Future<Integer> deleteTempTransactions(String summaryId, DBClient client) {
    Promise<Integer> promise = Promise.promise();
    Criterion criterion = getSummaryIdCriteria(summaryId);

    client.getPgClient()
      .delete(client.getConnection(), getTableName(), criterion, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(reply.result().rowCount());
        }
      });
    return promise.future();
  }

  public Future<Integer> deleteTempTransactionsWithNewConn(String summaryId, DBClient client) {
    Promise<Integer> promise = Promise.promise();
    Criterion criterion = getSummaryIdCriteria(summaryId);

    client.getPgClient()
      .delete(getTableName(), criterion, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(reply.result().rowCount());
        }
      });
    return promise.future();
  }

  public String getTableName() {
    return tableName;
  }

  protected abstract String createTempTransactionQuery(String tenantId);

  protected abstract Criterion getSummaryIdCriteria(String summaryId);

}
