package org.folio.rest.dao;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.Tx;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

import static org.folio.rest.persist.HelperUtils.getFullTableName;
import static org.folio.rest.persist.PostgresClient.pojo2json;
import static org.folio.rest.util.ResponseUtils.handleFailure;

public class TemporaryOrderTransactionPostgresDAO extends AbstractTemporaryTransactionsDAO implements TemporaryTransactionDAO {

  private static final String TEMPORARY_ORDER_TRANSACTIONS = "temporary_order_transactions";

  public static final String INSERT_TEMPORARY_ENCUMBRANCES = "INSERT INTO %s (id, jsonb) VALUES (?, ?::JSON) "
    + "ON CONFLICT (lower(f_unaccent(jsonb ->> 'amount'::text)), lower(f_unaccent(jsonb ->> 'fromFundId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePurchaseOrderId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'sourcePoLineId'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'initialAmountEncumbered'::text)), "
    + "lower(f_unaccent((jsonb -> 'encumbrance'::text) ->> 'status'::text))) DO UPDATE SET id = excluded.id RETURNING id;";

  protected TemporaryOrderTransactionPostgresDAO() {
    super(TEMPORARY_ORDER_TRANSACTIONS);
  }


//  public Future<List<Transaction>> createTempTransactionSequentially(Transaction transaction, String summaryId, Tx tx, Context context) {
//    Promise<List<Transaction>> promise = Promise.promise();
//    SharedData sharedData = context.owner().sharedData();
//    // define unique lockName based on combination of transactions type and summary id
//    String lockName = transaction.getTransactionType() + summaryId;
//
//    sharedData.getLock(lockName, lockResult -> {
//      if (lockResult.succeeded()) {
//        logger.info("Got lock {}", lockName);
//        Lock lock = lockResult.result();
//        try {
//          context.owner()
//            .setTimer(30000, timerId -> releaseLock(lock, lockName));
//
//          createTempTransaction(transaction, summaryId, tx)
//            .compose(transactions -> getTempTransactionsBySummaryId(summaryId, tx))
//            .onComplete(trnsResult -> {
//              releaseLock(lock, lockName);
//              if (trnsResult.succeeded()) {
//                promise.complete(trnsResult.result());
//              } else {
//                promise.fail(trnsResult.cause());
//              }
//          });
//        } catch (Exception e) {
//          releaseLock(lock, lockName);
//        }
//      } else {
//        promise.fail(new HttpStatusException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), lockResult.cause().getMessage()));
//      }
//    });
//
//    return promise.future();
//  }
//

  @Override
  protected String createTempTransactionQuery(String tenantId) {
    return String.format(INSERT_TEMPORARY_ENCUMBRANCES, getFullTableName(tenantId, getTableName()));
  }

  @Override
  public Criterion getSummaryIdCriteria(String summaryId) {
    return new CriterionBuilder().with("encumbrance_sourcePurchaseOrderId", summaryId).build();
  }

//  private void releaseLock(Lock lock, String lockName) {
//    logger.info("Released lock {}", lockName);
//    lock.release();
//  }

}
