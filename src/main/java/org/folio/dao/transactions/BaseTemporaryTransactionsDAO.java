package org.folio.dao.transactions;

import static org.folio.rest.persist.PostgresClient.pojo2JsonObject;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.sqlclient.Tuple;

public abstract class BaseTemporaryTransactionsDAO implements TemporaryTransactionDAO {

  protected final Logger logger = LogManager.getLogger(this.getClass());

  private final String tableName;

  protected BaseTemporaryTransactionsDAO(String tableName) {
    this.tableName = tableName;
  }

  @Override
  public Future<Transaction> createTempTransaction(Transaction transaction, String summaryId, DBClient client) {
    Promise<Transaction> promise = Promise.promise();

    if (transaction.getId() == null) {
      transaction.setId(UUID.randomUUID().toString());
    }

    logger.debug("Creating new transaction with id={}", transaction.getId());

    try {
      client.getPgClient().execute(createTempTransactionQuery(client.getTenantId()),
        Tuple.of(UUID.fromString(transaction.getId()), pojo2JsonObject(transaction)), reply -> {
        if (reply.succeeded()) {
          logger.debug("New transaction with id={} successfully created", transaction.getId());
          promise.complete(transaction);
        } else {
          logger.error("Transaction creation with id={} failed", transaction.getId(), reply.cause());
          handleFailure(promise, reply);
        }
      });
    } catch (Exception e) {
      promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
    }

    return promise.future();
  }


  @Override
  public Future<List<Transaction>> getTempTransactionsBySummaryId(String summaryId, DBClient client) {
    Promise<List<Transaction>> promise = Promise.promise();

    Criterion criterion = getSummaryIdCriteria(summaryId);

    client.getPgClient().get(tableName, Transaction.class, criterion, false, false, reply -> {
      if (reply.failed()) {
        logger.error("Failed to extract temporary transaction by summary id={}", summaryId, reply.cause());
        handleFailure(promise, reply);
      } else {
        List<Transaction> transactions = reply.result().getResults();
        promise.complete(transactions);
      }
    });
    return promise.future();
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

  public String getTableName() {
    return tableName;
  }

  protected abstract String createTempTransactionQuery(String tenantId);

  protected abstract Criterion getSummaryIdCriteria(String summaryId);

}
