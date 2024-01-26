package org.folio.dao.transactions;

import static org.folio.dao.transactions.EncumbranceDAO.TRANSACTIONS_TABLE;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.persist.HelperUtils.getFullTableName;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.vertx.sqlclient.SqlResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.interfaces.Results;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

public abstract class BaseTransactionDAO implements TransactionDAO {

  private static final Logger logger = LogManager.getLogger(BaseTransactionDAO.class);

  public static final String INSERT_PERMANENT_TRANSACTIONS_BY_IDS = "INSERT INTO %s (id, jsonb) (SELECT id, jsonb FROM %s WHERE id in (%s)) "
    + "ON CONFLICT DO NOTHING;";

  @Override
  public Future<List<Transaction>> getTransactions(Criterion criterion, DBConn conn) {
    logger.debug("Trying to get transactions by query: {}", criterion);
    return conn.get(TRANSACTIONS_TABLE, Transaction.class, criterion)
      .map(Results::getResults)
      .onSuccess(transactions -> logger.info("Successfully retrieved {} transactions", transactions.size()))
      .onFailure(e -> logger.error("Getting transactions failed", e));
  }

  @Override
  public Future<List<Transaction>> getTransactions(List<String> ids, DBConn conn) {
    logger.debug("Trying to get transactions by ids = {}", ids);
    if (ids.isEmpty()) {
      return Future.succeededFuture(Collections.emptyList());
    }
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    ids.forEach(id -> criterionBuilder.with("id", id));
    return getTransactions(criterionBuilder.build(), conn);
  }

  @Override
  public Future<Integer> saveTransactionsToPermanentTable(String summaryId, DBConn conn) {
    logger.debug("Trying to save transactions to permanent table with summaryid {}", summaryId);
    return conn.execute(createPermanentTransactionsQuery(conn.getTenantId()), Tuple.of(UUID.fromString(summaryId)))
      .map(SqlResult::rowCount)
      .onSuccess(rowCount -> logger.info("Successfully saved {} transactions to permanent table with summaryid {}",
        rowCount, summaryId))
      .onFailure(e -> logger.error("Saving transactions to permanent table with summaryid {} failed", summaryId, e));
  }

  @Override
  public Future<Integer> saveTransactionsToPermanentTable(List<String> ids, DBConn conn) {
    logger.debug("Trying to save transactions to permanent table by ids = {}", ids);
    return conn.execute(createPermanentTransactionsQuery(conn.getTenantId(), ids))
      .map(SqlResult::rowCount)
      .onSuccess(rowCount -> logger.info("Successfully saved {} transactions to permanent table with ids = {}",
        rowCount, ids))
      .onFailure(e -> logger.error("Save transactions to permanent table by ids = {} failed", ids, e));
  }

  protected abstract String createPermanentTransactionsQuery(String tenantId);

  protected String createPermanentTransactionsQuery(String tenantId, List<String> ids) {
    String idsAsString = ids.stream()
      .map(id -> StringUtils.wrap(id, "'"))
      .collect(Collectors.joining(","));
    return String.format(INSERT_PERMANENT_TRANSACTIONS_BY_IDS, getFullTableName(tenantId, TRANSACTIONS_TABLE), getFullTableName(tenantId, TEMPORARY_INVOICE_TRANSACTIONS), idsAsString);
  }

  @Override
  public Future<Void> updatePermanentTransactions(List<Transaction> transactions, DBConn conn) {
    logger.debug("Trying to update permanent transactions");
    if (transactions.isEmpty()) {
      return Future.succeededFuture();
    }
    List<String> ids = transactions.stream().map(Transaction::getId).toList();
    List<JsonObject> jsonTransactions = transactions.stream().map(JsonObject::mapFrom).collect(Collectors.toList());
    String sql = buildUpdatePermanentTransactionQuery(jsonTransactions, conn.getTenantId());
    return conn.execute(sql)
      .onSuccess(rowSet -> logger.info("updatePermanentTransactions:: success updating permanent transactions, ids = {}",
        ids))
      .onFailure(t -> logger.error("updatePermanentTransactions:: failed updating permanent transactions, ids = {}",
        ids, t))
      .mapEmpty();
  }

  @Override
  public Future<Void> deleteTransactions(Criterion criterion, DBConn conn) {
    logger.debug("Trying to delete transactions by query: {}", criterion);
    return conn.delete(TRANSACTIONS_TABLE, criterion)
      .onSuccess(rowSet -> logger.info("deleteTransactions:: success deleting {} transactions", rowSet.rowCount()))
      .onFailure(t -> logger.error("deleteTransactions:: failed deleting transactions, criterion = {}", criterion, t))
      .mapEmpty();
  }

  @Override
  public Future<Transaction> createTransaction(Transaction transaction, DBConn conn) {
    logger.debug("createTransaction:: Trying to create transaction");
    if (StringUtils.isEmpty(transaction.getId())) {
      transaction.setId(UUID.randomUUID().toString());
    }
    return conn.saveAndReturnUpdatedEntity(TRANSACTIONS_TABLE, transaction.getId(), transaction)
      .onSuccess(id -> logger.info("createTransaction:: Transaction with id {} successfully created", id))
      .onFailure(t -> logger.error("createTransaction:: Creating transaction  with id {} failed", transaction.getId(), t));
  }

  @Override
  public Future<Void> deleteTransactionById(String id, DBConn conn) {
    logger.debug("Trying to delete transaction by id {}", id);
    return conn.delete(TRANSACTIONS_TABLE, id)
      .onSuccess(s -> logger.info("Successfully deleted a transaction with id {}", id))
      .onFailure(t -> logger.error("Deleting transaction by id {} failed", id, t))
      .mapEmpty();
  }

  @Override
  public Future<Void> updateTransaction(Transaction transaction, DBConn conn) {
    logger.debug("updateTransaction:: Trying to update transaction with id {}", transaction.getId());
    return conn.update(TRANSACTIONS_TABLE, transaction, transaction.getId())
      .onSuccess(rowSet -> logger.info("updateTransaction:: Transaction with id {} successfully updated", transaction.getId()))
      .onFailure(t -> logger.error("updateTransaction:: Updating transaction with id {} failed", transaction.getId(), t))
      .mapEmpty();
  }

  protected abstract String buildUpdatePermanentTransactionQuery(List<JsonObject> jsonTransactions, String tenantId);
}
