package org.folio.rest.dao;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.folio.rest.persist.HelperUtils.ID_FIELD_NAME;
import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.PostgresClient.pojo2json;
import static org.folio.rest.util.ResponseUtils.handleFailure;

public class EncumbrancesTransactionalDAO implements TransactionsTransactionalDAO {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  private static final String TEMPORARY_INVOICE_TRANSACTIONS = "temporary_invoice_transactions";
  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";
  private static final String TRANSACTIONS_TABLE = "transaction";

  public static final String INSERT_PERMANENT_ENCUMBRANCES = "INSERT INTO %s (id, jsonb) SELECT id, jsonb FROM %s WHERE encumbrance_sourcePurchaseOrderId = ? "
    + "ON CONFLICT DO NOTHING;";

  public static final String SELECT_PERMANENT_TRANSACTIONS = "SELECT DISTINCT ON (permtransactions.id) permtransactions.jsonb FROM %s AS permtransactions INNER JOIN %s AS transactions "
    + "ON transactions.paymentEncumbranceId = permtransactions.id WHERE transactions.sourceInvoiceId = ?";

  @Override
  public Future<List<Transaction>> getTransactionsBySummaryId(String summaryId, Tx tx) {
    Promise<List<Transaction>> promise = Promise.promise();
    String sql = buildGetPermanentEncumbrancesQuery(tx.getTenantId());
    JsonArray params = new JsonArray();
    params.add(summaryId);
    tx.getPgClient()
      .select(tx.getConnection(), sql, params, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Transaction> encumbrances = reply.result()
            .getResults()
            .stream()
            .flatMap(JsonArray::stream)
            .map(o -> new JsonObject(o.toString()).mapTo(Transaction.class))
            .collect(Collectors.toList());
          promise.complete(encumbrances);
        }
      });
    return promise.future();
  }

  @Override
  public Future<Integer> saveTransactionsToPermanentTable(String summaryId, Tx tx) {
    Promise<Integer> promise = Promise.promise();
    JsonArray param = new JsonArray();
    param.add(summaryId);
    tx.getPgClient()
      .execute(tx.getConnection(), createPermanentTransactionsQuery(tx.getTenantId()), param, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(reply.result().getUpdated());
        }
      });
    return promise.future();
  }


  public Future<Integer> deleteTempTransactionsBySummaryId(String summaryId, Tx<List<Transaction>> tx) {
    Promise<Integer> promise = Promise.promise();

    Criterion criterion = new CriterionBuilder().with("encumbrance_sourcePurchaseOrderId", summaryId).build();

    tx.getPgClient()
      .delete(tx.getConnection(), TEMPORARY_ORDER_TRANSACTIONS, criterion, reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          promise.complete(reply.result().getUpdated());
        }
      });
    return promise.future();
  }


  protected String createPermanentTransactionsQuery(String tenantId) {
    return String.format(INSERT_PERMANENT_ENCUMBRANCES, getFullTableName(tenantId, TRANSACTIONS_TABLE), getFullTableName(tenantId, TEMPORARY_ORDER_TRANSACTIONS));
  }

  private String buildGetPermanentEncumbrancesQuery(String tenantId) {
    return String.format(SELECT_PERMANENT_TRANSACTIONS, getFullTableName(tenantId, TRANSACTIONS_TABLE),
      getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS));
  }
}
