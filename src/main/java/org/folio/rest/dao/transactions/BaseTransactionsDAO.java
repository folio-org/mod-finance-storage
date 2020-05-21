package org.folio.rest.dao.transactions;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;

import java.util.List;
import java.util.stream.Collectors;

import static org.folio.rest.dao.transactions.EncumbrancesDAO.TRANSACTIONS_TABLE;
import static org.folio.rest.util.ResponseUtils.handleFailure;

public abstract class BaseTransactionsDAO implements TransactionsDAO {

  @Override
  public Future<List<Transaction>> getTransactions(Criterion criterion, DBClient client) {
    Promise<List<Transaction>> promise = Promise.promise();
    client.getPgClient()
      .get(client.getConnection(), TRANSACTIONS_TABLE, Transaction.class, criterion, false, true, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Transaction> encumbrances = reply.result().getResults();
          promise.complete(encumbrances);
        }
      });
    return promise.future();
  }

  @Override
  public Future<Integer> saveTransactionsToPermanentTable(String summaryId, DBClient client) {
    Promise<Integer> promise = Promise.promise();
    JsonArray param = new JsonArray();
    param.add(summaryId);
    client.getPgClient()
      .execute(client.getConnection(), createPermanentTransactionsQuery(client.getTenantId()), param, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(reply.result().getUpdated());
        }
      });
    return promise.future();
  }

  protected abstract String createPermanentTransactionsQuery(String tenantId);

  @Override
  public Future<Void> updatePermanentTransactions(List<Transaction> transactions, DBClient client) {
    Promise<Void> promise = Promise.promise();
    if (transactions.isEmpty()) {
      promise.complete();
    } else {
      List<JsonObject> jsonTransactions = transactions.stream().map(JsonObject::mapFrom).collect(Collectors.toList());
      String sql = buildUpdatePermanentTransactionQuery(jsonTransactions, client.getTenantId());
      client.getPgClient()
        .execute(client.getConnection(), sql, reply -> {
          if (reply.failed()) {
            handleFailure(promise, reply);
          } else {
            promise.complete();
          }
        });
    }
    return promise.future();
  }

  protected abstract String buildUpdatePermanentTransactionQuery(List<JsonObject> jsonTransactions, String tenantId);
}
