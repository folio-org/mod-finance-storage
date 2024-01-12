package org.folio.dao.transactions;

import static java.util.stream.Collectors.toMap;
import static org.folio.dao.summary.InvoiceTransactionSummaryDAO.INVOICE_TRANSACTION_SUMMARIES;
import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.impl.ExpenseClassAPI.EXPENSE_CLASS_TABLE;
import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ALLOCATION;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.service.transactions.AbstractTransactionService.TRANSACTION_TABLE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.ExpenseClass;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBConn;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.http.Header;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class PendingPaymentDAOTest extends TestBase {

  static final String TEST_TENANT = "test_tenant";
  private static final Header TEST_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TEST_TENANT);

  final private PendingPaymentDAO pendingPaymentDAO = new PendingPaymentDAO();
  private static TenantJob tenantJob;

  @BeforeEach
  void prepareData() throws Exception {
   tenantJob = prepareTenant(TEST_TENANT_HEADER, false, false);
  }

  @AfterEach
  void cleanupData() {
    purge(TEST_TENANT_HEADER);
  }

  @AfterAll
  static void deleteTable() {
    deleteTenant(tenantJob, TEST_TENANT_HEADER);
  }

  @Test
  void tesGetTransactions(Vertx vertx, VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    Transaction transaction = new Transaction().withId(id);
    Promise<Void> promise1 = Promise.promise();
    final DBClient client = new DBClient(vertx, TEST_TENANT);
    client.getPgClient().save(TRANSACTION_TABLE, id, transaction, event -> {
      promise1.complete();
    });
    Promise<Void> promise2 = Promise.promise();
    client.getPgClient().save(TRANSACTION_TABLE, transaction, event -> {
      promise2.complete();
    });
    Criterion criterion = new Criterion().addCriterion(new Criteria().addField("id").setOperation("=").setVal(id).setJSONB(false));
    testContext.assertComplete(promise1.future()
        .compose(aVoid -> promise2.future())
        .compose(o -> client.withConn(conn -> pendingPaymentDAO.getTransactions(criterion, conn))))
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
      client.withTrans(conn -> promise1.future()
        .compose(id1 -> promise2.future())
        .compose(id2 -> pendingPaymentDAO.getTransactions(new Criterion(), conn))
        .compose(transactions -> {
          assertThat(transactions, hasSize(2));
          t1.setId(transactions.get(0).getId());
          t2.setId(transactions.get(1).getId());
          transactions.get(0).withTransactionType(PENDING_PAYMENT);
          transactions.get(1).withTransactionType(ALLOCATION);
          return pendingPaymentDAO.updatePermanentTransactions(transactions, conn);
        })
      )
      .compose(o -> client.withConn(conn -> pendingPaymentDAO.getTransactions(new Criterion(), conn)))
    ).onComplete(event -> {
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
  void testSaveTransactionsToPermanentTableOnlyPendingPayments(Vertx vertx, VertxTestContext testContext) {
    String summaryId = UUID.randomUUID().toString();
    Transaction tmpTransaction = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(summaryId);

    final DBClient client = new DBClient(vertx, TEST_TENANT);

    testContext.assertComplete(
      client.withTrans(conn -> createSummary(summaryId, conn)
        .compose(s -> createTmpTransaction(tmpTransaction, conn))
        .compose(id1 -> pendingPaymentDAO.saveTransactionsToPermanentTable(summaryId, conn))
      ).compose(o -> client.withConn(conn -> pendingPaymentDAO.getTransactions(new Criterion(), conn)))
    ).onComplete(event -> {
      List<Transaction> transactions = event.result();
      testContext.verify(() -> {
        assertThat(transactions, hasSize(1));
        assertThat(transactions.get(0).getId(), is(tmpTransaction.getId()));
        assertThat(transactions.get(0).getTransactionType(), is(PENDING_PAYMENT));
      });
      testContext.completeNow();
    });
  }

  @Test
  void shouldDoNothingOnConflictWhenSaveTransactionsToTransactionTable(Vertx vertx, VertxTestContext testContext) {

    Fund fund = new Fund()
      .withId(UUID.randomUUID().toString());

    FiscalYear fiscalYear = new FiscalYear()
      .withId(UUID.randomUUID().toString());

    ExpenseClass expenseClass = new ExpenseClass()
      .withId(UUID.randomUUID().toString());

    String summaryId = UUID.randomUUID().toString();
    Transaction tmpTransaction1 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(summaryId)
      .withSourceInvoiceLineId(UUID.randomUUID().toString())
      .withAmount(100d)
      .withFromFundId(fund.getId())
      .withExpenseClassId(expenseClass.getId())
      .withFiscalYearId(fiscalYear.getId());


    Transaction tmpTransaction2 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(summaryId)
      .withSourceInvoiceLineId(tmpTransaction1.getSourceInvoiceLineId())
      .withAmount(tmpTransaction1.getAmount())
      .withFromFundId(tmpTransaction1.getFromFundId())
      .withExpenseClassId(tmpTransaction1.getExpenseClassId())
      .withFiscalYearId(tmpTransaction1.getFiscalYearId())
      .withCurrency("TEST");

    final DBClient client = new DBClient(vertx, TEST_TENANT);

    testContext.assertComplete(
      client.withTrans(conn -> createFund(fund, conn)
        .compose(s -> createFiscalYear(fiscalYear, conn))
        .compose(s -> createExpenseClass(expenseClass, conn))
        .compose(client1 -> createSummary(summaryId, conn))
        .compose(s -> createTmpTransaction(tmpTransaction1, conn))
        .compose(id1 -> pendingPaymentDAO.saveTransactionsToPermanentTable(summaryId, conn))
        .compose(integer -> deleteTmpTransaction(tmpTransaction1, conn))
        .compose(aVoid -> createTmpTransaction(tmpTransaction2, conn))
        .compose(id1 -> pendingPaymentDAO.saveTransactionsToPermanentTable(summaryId, conn))
      ).compose(o -> client.withConn(conn -> pendingPaymentDAO.getTransactions(new Criterion(), conn)))
    ).onComplete(event -> {
      List<Transaction> transactions = event.result();
      testContext.verify(() -> {
        assertThat(transactions, Matchers.hasSize(1));
        assertThat(transactions.get(0).getId(), is(tmpTransaction1.getId()));
        assertThat(transactions.get(0).getTransactionType(), is(PENDING_PAYMENT));
        assertNull(transactions.get(0).getCurrency());
      });
      testContext.completeNow();
    });
  }

  @Test
  void shouldCreateBothTransactionsWhenSaveTransactionsWithDifferentExpenseClassIds(Vertx vertx, VertxTestContext testContext) {

    Fund fund = new Fund()
      .withId(UUID.randomUUID().toString());

    FiscalYear fiscalYear = new FiscalYear()
      .withId(UUID.randomUUID().toString());

    ExpenseClass expenseClass1 = new ExpenseClass()
      .withId(UUID.randomUUID().toString());

    ExpenseClass expenseClass2 = new ExpenseClass()
      .withId(UUID.randomUUID().toString());

    String summaryId = UUID.randomUUID().toString();
    Transaction tmpTransaction1 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(summaryId)
      .withSourceInvoiceLineId(UUID.randomUUID().toString())
      .withAmount(100d)
      .withFromFundId(fund.getId())
      .withExpenseClassId(expenseClass1.getId())
      .withFiscalYearId(fiscalYear.getId());


    Transaction tmpTransaction2 = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(summaryId)
      .withSourceInvoiceLineId(tmpTransaction1.getSourceInvoiceLineId())
      .withAmount(tmpTransaction1.getAmount())
      .withFromFundId(tmpTransaction1.getFromFundId())
      .withExpenseClassId(expenseClass2.getId())
      .withFiscalYearId(tmpTransaction1.getFiscalYearId());

    final DBClient client = new DBClient(vertx, TEST_TENANT);

    testContext.assertComplete(
      client.withTrans(conn -> createFund(fund, conn)
        .compose(s -> createFiscalYear(fiscalYear, conn))
        .compose(s -> createExpenseClass(expenseClass1, conn))
        .compose(s -> createExpenseClass(expenseClass2, conn))
        .compose(client1 -> createSummary(summaryId, conn))
        .compose(s -> createTmpTransaction(tmpTransaction1, conn))
        .compose(id1 -> pendingPaymentDAO.saveTransactionsToPermanentTable(summaryId, conn))
        .compose(integer -> deleteTmpTransaction(tmpTransaction1, conn))
        .compose(aVoid -> createTmpTransaction(tmpTransaction2, conn))
        .compose(id1 -> pendingPaymentDAO.saveTransactionsToPermanentTable(summaryId, conn))
      ).compose(o -> client.withConn(conn -> pendingPaymentDAO.getTransactions(new Criterion(), conn)))
    ).onComplete(event -> {
      List<Transaction> transactions = event.result();
      testContext.verify(() -> assertThat(transactions, Matchers.hasSize(2)));
      testContext.completeNow();
    });
  }

  private Future<String> createTmpTransaction(Transaction tmpTransaction, DBConn conn) {
    return conn.save(TEMPORARY_INVOICE_TRANSACTIONS, tmpTransaction.getId(), tmpTransaction);
  }

  private Future<Void> deleteTmpTransaction(Transaction tmpTransaction, DBConn conn) {
    return conn.delete(TEMPORARY_INVOICE_TRANSACTIONS, tmpTransaction.getId())
      .mapEmpty();
  }

  private Future<String> createSummary(String summaryId, DBConn conn) {
    return conn.save(INVOICE_TRANSACTION_SUMMARIES, summaryId, new InvoiceTransactionSummary().withNumPendingPayments(1).withId(summaryId));
  }

  private Future<String> createFund(Fund fund, DBConn conn) {
    return conn.save(FUND_TABLE, fund.getId(), fund);
  }

  private Future<String> createFiscalYear(FiscalYear fiscalYear, DBConn conn) {
    return conn.save(FISCAL_YEAR_TABLE, fiscalYear.getId(), fiscalYear);
  }

  private Future<String> createExpenseClass(ExpenseClass expenseClass, DBConn conn) {
    return conn.save(EXPENSE_CLASS_TABLE, expenseClass.getId(), expenseClass);
  }
}
