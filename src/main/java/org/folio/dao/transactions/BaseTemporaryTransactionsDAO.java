package org.folio.dao.transactions;

import static org.folio.rest.persist.PostgresClient.pojo2JsonObject;
import static org.folio.rest.util.ResponseUtils.handleFailure;

import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.Response;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.TempTransaction;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
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
  public Future<TempTransaction> createTempTransaction(Transaction transaction, String summaryId, DBClient client) {
    Promise<TempTransaction> promise = Promise.promise();

    if (transaction.getId() == null) {
      transaction.setId(UUID.randomUUID().toString());
    }

    logger.debug("Creating new temp transaction with id={}", transaction.getId());

    TempTransaction tempTrans = JsonObject.mapFrom(transaction).mapTo(TempTransaction.class);
    tempTrans.setTransactionSummaryId(summaryId);
    try {
      client.getPgClient().execute(createTempTransactionQuery(client.getTenantId()),
        Tuple.of(UUID.fromString(tempTrans.getId()), pojo2JsonObject(tempTrans)), reply -> {
        if (reply.succeeded()) {
          logger.debug("New temp transaction with id={} successfully created", tempTrans.getId());
          promise.complete(tempTrans);
        } else {
          logger.error("Temp transaction creation with id={} failed", tempTrans.getId(), reply.cause());
          handleFailure(promise, reply);
        }
      });
    } catch (Exception e) {
      promise.fail(new HttpException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()));
    }

    return promise.future();
  }


  @Override
  public Future<List<TempTransaction>> getTempTransactionsBySummaryId(String summaryId, DBClient client) {
    Promise<List<TempTransaction>> promise = Promise.promise();

    Criterion criterion = getSummaryIdCriteria(summaryId);

    client.getPgClient().get(tableName, TempTransaction.class, criterion, false, false, reply -> {
      if (reply.failed()) {
        logger.error("Failed to extract temporary transaction by summary id={}", summaryId, reply.cause());
        handleFailure(promise, reply);
      } else {
        List<TempTransaction> transactions = reply.result().getResults();
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

  public Criterion getSummaryIdCriteria(String summaryId) {
    return new CriterionBuilder().with("transactionSummaryId", summaryId).build();
  }


}
