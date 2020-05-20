package org.folio.rest.dao.transactions;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;

import java.util.List;
import java.util.stream.Collectors;

import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.util.ResponseUtils.handleFailure;

public class EncumbrancesDAO implements TransactionsDAO {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";
  public static final String TRANSACTIONS_TABLE = "transaction";

  public static final String INSERT_PERMANENT_ENCUMBRANCES = "INSERT INTO %s (id, jsonb) SELECT id, jsonb FROM %s WHERE encumbrance_sourcePurchaseOrderId = ? "
    + "ON CONFLICT DO NOTHING;";


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

  private String buildUpdatePermanentTransactionQuery(List<JsonObject> transactions, String tenantId) {
    return String.format("UPDATE %s AS transactions " +
      "SET jsonb = t.jsonb FROM (VALUES  %s) AS t (id, jsonb) " +
      "WHERE t.id::uuid = transactions.id;", getFullTableName(tenantId, TRANSACTIONS_TABLE), HelperUtils.getQueryValues(transactions));
  }

  protected String createPermanentTransactionsQuery(String tenantId) {
    return String.format(INSERT_PERMANENT_ENCUMBRANCES, getFullTableName(tenantId, TRANSACTIONS_TABLE), getFullTableName(tenantId, TEMPORARY_ORDER_TRANSACTIONS));
  }

}
