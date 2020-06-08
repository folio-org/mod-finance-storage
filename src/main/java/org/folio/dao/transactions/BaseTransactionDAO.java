package org.folio.dao.transactions;

import static org.folio.dao.transactions.EncumbranceDAO.TRANSACTIONS_TABLE;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.util.ResponseUtils.handleVoidAsyncResult;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class BaseTransactionDAO implements TransactionDAO {

  public static final String INSERT_PERMANENT_TRANSACTIONS_BY_IDS = "INSERT INTO %s (id, jsonb) (SELECT id, jsonb FROM %s WHERE id in (%s)) "
    + "ON CONFLICT DO NOTHING;";

  @Override
  public Future<List<Transaction>> getTransactions(Criterion criterion, DBClient client) {
    Promise<List<Transaction>> promise = Promise.promise();
    if (Objects.isNull(client.getConnection())) {
      client.getPgClient().get(TRANSACTIONS_TABLE, Transaction.class, criterion, false, true, handleGet(promise));
    } else {
      client.getPgClient()
        .get(client.getConnection(), TRANSACTIONS_TABLE, Transaction.class, criterion, false, true, handleGet(promise));
    }
    return promise.future();
  }

  private Handler<AsyncResult<Results<Transaction>>> handleGet(Promise<List<Transaction>> promise) {
    return reply -> {
      if (reply.failed()) {
        handleFailure(promise, reply);
      } else {
        List<Transaction> encumbrances = reply.result().getResults();
        promise.complete(encumbrances);
      }
    };
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

  @Override
  public Future<Integer> saveTransactionsToPermanentTable(List<String> ids, DBClient client) {
    Promise<Integer> promise = Promise.promise();
    JsonArray param = new JsonArray();
    ids.forEach(param::add);

    client.getPgClient()
      .execute(client.getConnection(), createPermanentTransactionsQuery(client.getTenantId(), ids), reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(reply.result().getUpdated());
        }
      });
    return promise.future();
  }

  protected abstract String createPermanentTransactionsQuery(String tenantId);

  protected String createPermanentTransactionsQuery(String tenantId, List<String> ids) {
    String idsAsString = ids.stream()
      .map(id -> StringUtils.wrap(id, "'"))
      .collect(Collectors.joining(","));
    return String.format(INSERT_PERMANENT_TRANSACTIONS_BY_IDS, getFullTableName(tenantId, TRANSACTIONS_TABLE), getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS), idsAsString);
  }

  @Override
  public Future<Void> updatePermanentTransactions(List<Transaction> transactions, DBClient client) {
    Promise<Void> promise = Promise.promise();
    if (transactions.isEmpty()) {
      promise.complete();
    } else {
      List<JsonObject> jsonTransactions = transactions.stream().map(JsonObject::mapFrom).collect(Collectors.toList());
      String sql = buildUpdatePermanentTransactionQuery(jsonTransactions, client.getTenantId());
      client.getPgClient()
        .execute(client.getConnection(), sql, reply -> handleVoidAsyncResult(promise, reply));
    }
    return promise.future();
  }

  @Override
  public Future<Void> deleteTransactions(Criterion criterion, DBClient client) {
    Promise<Void> promise = Promise.promise();
    client.getPgClient().delete(client.getConnection(), TRANSACTIONS_TABLE, criterion, event -> handleVoidAsyncResult(promise, event));
    return promise.future();
  }

  protected abstract String buildUpdatePermanentTransactionQuery(List<JsonObject> jsonTransactions, String tenantId);
}
