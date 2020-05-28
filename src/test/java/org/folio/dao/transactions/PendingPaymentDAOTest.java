package org.folio.dao.transactions;

import io.restassured.http.Header;
import io.vertx.core.Promise;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criterion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static org.folio.dao.summary.InvoiceTransactionSummaryDAO.INVOICE_TRANSACTION_SUMMARIES;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ALLOCATION;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.service.transactions.AbstractTransactionService.TRANSACTION_TABLE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@ExtendWith(VertxExtension.class)
public class PendingPaymentDAOTest extends TestBase {

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  private PendingPaymentDAO pendingPaymentDAO = new PendingPaymentDAO();

  @BeforeEach
  void prepareData() throws MalformedURLException {
    prepareTenant(TEST_TENANT_HEADER, false, false);
  }

  @AfterEach
  void cleanupData() throws MalformedURLException {
    deleteTenant(TEST_TENANT_HEADER);
  }

  @Test
  void tesGetTransactions(Vertx vertx, VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    Transaction transaction = new Transaction().withId(id);
    Promise<Void> promise1 = Promise.promise();
    new DBClient(vertx, TEST_TENANT).getPgClient().save(TRANSACTION_TABLE, id, transaction, event -> {
      promise1.complete();
    });
    Promise<Void> promise2 = Promise.promise();
    new DBClient(vertx, TEST_TENANT).getPgClient().save(TRANSACTION_TABLE, transaction, event -> {
      promise2.complete();
    });
    Criterion criterion = new Criterion().addCriterion(new Criteria().addField("id").setOperation("=").setVal(id).setJSONB(false));
    testContext.assertComplete(promise1.future()
      .compose(aVoid -> promise2.future())
      .compose(o -> pendingPaymentDAO.getTransactions(criterion, new DBClient(vertx, TEST_TENANT))))
      .onComplete(event -> {
        List<Transaction> transactions = event.result();
        testContext.verify(() -> {
          assertThat(transactions, hasSize(1));
          assertThat(transactions.get(0).getId(), is(id));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testUpdatePermanentTransactions(Vertx vertx, VertxTestContext testContext) {
    Transaction emptyTransaction = new Transaction();
    Transaction t1 = new Transaction();
    Transaction t2 = new Transaction();
    Promise<String> promise1 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(TRANSACTION_TABLE, emptyTransaction, event -> {
      promise1.complete(event.result());
    });
    Promise<String> promise2 = Promise.promise();
    client.getPgClient().save(TRANSACTION_TABLE, emptyTransaction, event -> {
      promise2.complete(event.result());
    });
    testContext.assertComplete(
      client.startTx()
      .compose(v -> promise1.future())
      .compose(id1 -> promise2.future()
      .compose(id2 -> {
        t1.withId(id1).withTransactionType(PENDING_PAYMENT);
        t2.withId(id2).withTransactionType(ALLOCATION);
        return pendingPaymentDAO.updatePermanentTransactions(Arrays.asList(t1, t2), client);
      }))
        .compose(aVoid -> client.endTx())
      .compose(o -> pendingPaymentDAO.getTransactions(new Criterion(), new DBClient(vertx, TEST_TENANT))))
      .onComplete(event -> {
        List<Transaction> transactions = event.result();
        testContext.verify(() -> {
          assertThat(transactions, hasSize(2));
          Map<String, Transaction> transactionMap = transactions.stream().collect(toMap(Transaction::getId, Function.identity()));
          assertThat(transactionMap.get(t1.getId()).getId(), is(t1.getId()));
          assertThat(transactionMap.get(t2.getId()).getId(), is(t2.getId()));
          assertThat(transactionMap.get(t1.getId()).getTransactionType(), is(PENDING_PAYMENT));
          assertThat(transactionMap.get(t2.getId()).getTransactionType(), is(ALLOCATION));
        });
        testContext.completeNow();
      });

  }

  @Test
  void testSaveTransactionsToPermanentTableOnluPendingPayments(Vertx vertx, VertxTestContext testContext) {
    String summaryId = UUID.randomUUID().toString();
    Transaction tmpTransaction = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(summaryId);


    final DBClient client = new DBClient(vertx, TEST_TENANT);
    Promise<String> promise1 = Promise.promise();
    client.getPgClient().save(INVOICE_TRANSACTION_SUMMARIES, summaryId, new InvoiceTransactionSummary().withNumPendingPayments(1).withId(summaryId), event -> {
      promise1.complete(event.result());
    });
    Promise<String> promise2 = Promise.promise();
    client.getPgClient().save(TEMPORARY_INVOICE_TRANSACTIONS, tmpTransaction.getId(), tmpTransaction, event -> promise2.complete(event.result()));

    testContext.assertComplete(
      promise1.future()
        .compose(s -> promise2.future())
        .compose(s -> client.startTx())
        .compose(id1 -> pendingPaymentDAO.saveTransactionsToPermanentTable(summaryId, client))
        .compose(aVoid -> client.endTx())
        .compose(o -> pendingPaymentDAO.getTransactions(new Criterion(), new DBClient(vertx, TEST_TENANT))))
      .onComplete(event -> {
        List<Transaction> transactions = event.result();
        testContext.verify(() -> {
          assertThat(transactions, hasSize(1));
          assertThat(transactions.get(0).getId(), is(tmpTransaction.getId()));
          assertThat(transactions.get(0).getTransactionType(), is(PENDING_PAYMENT));
        });
        testContext.completeNow();
      });
  }


}
