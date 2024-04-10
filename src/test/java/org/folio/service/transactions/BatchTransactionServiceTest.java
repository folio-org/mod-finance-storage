package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.impl.RowImpl;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.RowDesc;
import org.folio.dao.budget.BudgetDAO;
import org.folio.dao.budget.BudgetPostgresDAO;
import org.folio.dao.fund.FundDAO;
import org.folio.dao.fund.FundPostgresDAO;
import org.folio.dao.ledger.LedgerDAO;
import org.folio.dao.ledger.LedgerPostgresDAO;
import org.folio.dao.transactions.BatchTransactionDAO;
import org.folio.dao.transactions.BatchTransactionPostgresDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.AwaitingPayment;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.TransactionPatch;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.helpers.LocalRowDesc;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.folio.rest.persist.interfaces.Results;
import org.folio.service.budget.BudgetService;
import org.folio.service.fund.FundService;
import org.folio.service.fund.StorageFundService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledger.StorageLedgerService;
import org.folio.service.transactions.batch.BatchAllocationService;
import org.folio.service.transactions.batch.BatchEncumbranceService;
import org.folio.service.transactions.batch.BatchPaymentCreditService;
import org.folio.service.transactions.batch.BatchPendingPaymentService;
import org.folio.service.transactions.batch.BatchTransactionService;
import org.folio.service.transactions.batch.BatchTransactionServiceInterface;
import org.folio.service.transactions.batch.BatchTransferService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.dao.ledger.LedgerPostgresDAO.LEDGER_TABLE;
import static org.folio.dao.transactions.BatchTransactionPostgresDAO.TRANSACTIONS_TABLE;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.jaxrs.model.Budget.BudgetStatus.ACTIVE;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.RELEASED;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.UNRELEASED;
import static org.folio.rest.jaxrs.model.Transaction.Source.PO_LINE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ALLOCATION;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ENCUMBRANCE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.TRANSFER;
import static org.folio.rest.util.ErrorCodes.BUDGET_RESTRICTED_ENCUMBRANCE_ERROR;
import static org.folio.rest.util.ErrorCodes.BUDGET_RESTRICTED_EXPENDITURES_ERROR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(VertxExtension.class)
public class BatchTransactionServiceTest {
  private AutoCloseable mockitoMocks;
  private BatchTransactionService batchTransactionService;
  @Mock
  private DBClientFactory dbClientFactory;
  @Mock
  private RequestContext requestContext;
  @Mock
  private DBClient dbClient;
  @Mock
  private DBConn conn;

  @BeforeEach
  public void init() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    FundDAO fundDAO = new FundPostgresDAO();
    FundService fundService = new StorageFundService(fundDAO);
    BudgetDAO budgetDAO = new BudgetPostgresDAO();
    BudgetService budgetService = new BudgetService(budgetDAO);
    LedgerDAO ledgerDAO = new LedgerPostgresDAO();
    LedgerService ledgerService = new StorageLedgerService(ledgerDAO, fundService);
    Set<BatchTransactionServiceInterface> batchTransactionStrategies = new HashSet<>();
    batchTransactionStrategies.add(new BatchEncumbranceService());
    batchTransactionStrategies.add(new BatchPendingPaymentService());
    batchTransactionStrategies.add(new BatchPaymentCreditService());
    batchTransactionStrategies.add(new BatchAllocationService());
    batchTransactionStrategies.add(new BatchTransferService());
    BatchTransactionDAO transactionDAO = new BatchTransactionPostgresDAO();
    batchTransactionService = new BatchTransactionService(dbClientFactory, transactionDAO, fundService, budgetService,
      ledgerService, batchTransactionStrategies);
    doReturn(dbClient)
      .when(dbClientFactory).getDbClient(eq(requestContext));
    doAnswer(invocation -> {
      Function<DBConn, Future<Void>> function = invocation.getArgument(0);
      return function.apply(conn);
    }).when(dbClient).withTrans(any());
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void testBatchEntityValidity(VertxTestContext testContext) {
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";
    Transaction pendingPayment = new Transaction()
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withCurrency(currency)
      .withTransactionType(PENDING_PAYMENT);
    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(pendingPayment);
    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onFailure(thrown -> {
        testContext.verify(() -> {
          assertThat(thrown, instanceOf(HttpException.class));
          assertThat(((HttpException) thrown).getCode(), equalTo(400));
          assertThat(thrown.getMessage(), equalTo("Id is required in transactions to create."));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreateEncumbrance(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction encumbrance = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(Encumbrance.Status.PENDING)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(5d));

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(encumbrance);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 0d, false, false);

    Criterion transactionCriterion = createCriterionByIds(List.of(transactionId));
    doReturn(succeededFuture(createResults(new ArrayList<Transaction>())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).saveBatch(anyString(), anyList());

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify encumbrance creation
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> saveEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();
          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(saveEntities.get(0).get(0));
          assertNotNull(savedTransaction.getMetadata());
          assertThat(savedTransaction.getAmount(), equalTo(encumbrance.getAmount()));

          // Verify budget update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> updateEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(1)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          assertThat(updateTableNames.get(0), equalTo(BUDGET_TABLE));
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();
          Budget savedBudget = (Budget)(updateEntities.get(0).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(5d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testUpdateEncumbrance(VertxTestContext testContext) {
    String encumbranceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(Encumbrance.Status.PENDING)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(5d))
      .withMetadata(new Metadata());

    Transaction newEncumbrance = JsonObject.mapFrom(existingEncumbrance).mapTo(Transaction.class);
    newEncumbrance.setAmount(10d);

    Batch batch = new Batch();
    batch.getTransactionsToUpdate().add(newEncumbrance);

    setupFundBudgetLedger(fundId, fiscalYearId, 5d, 0d, 0d, false, false);

    Criterion transactionCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify encumbrance update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> updateEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(updateEntities.get(0).get(0));
          assertNotNull(savedTransaction.getMetadata().getUpdatedDate());
          assertThat(savedTransaction.getAmount(), equalTo(10d));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(10d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreatePendingPaymentWithLinkedEncumbrance(VertxTestContext testContext) {
    String pendingPaymentId = UUID.randomUUID().toString();
    String encumbranceId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

    Transaction pendingPayment = new Transaction()
      .withId(pendingPaymentId)
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(5d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withAwaitingPayment(new AwaitingPayment()
        .withEncumbranceId(encumbranceId)
        .withReleaseEncumbrance(true));

    Transaction existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(UNRELEASED)
        .withAmountAwaitingPayment(0d)
        .withInitialAmountEncumbered(5d))
      .withMetadata(new Metadata());

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(pendingPayment);

    setupFundBudgetLedger(fundId, fiscalYearId, 5d, 0d, 0d, false, false);

    Criterion pendingPaymentCriterion = createCriterionByIds(List.of(pendingPaymentId));
    doReturn(succeededFuture(createResults(List.of())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(pendingPaymentCriterion.toString())));

    Criterion encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(encumbranceCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).saveBatch(anyString(), anyList());

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify pending payment creation
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> saveEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();

          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedPendingPayment = (Transaction)(saveEntities.get(0).get(0));
          assertThat(savedPendingPayment.getTransactionType(), equalTo(PENDING_PAYMENT));
          assertNotNull(savedPendingPayment.getMetadata().getUpdatedDate());
          assertThat(savedPendingPayment.getAmount(), equalTo(5d));

          // Verify encumbrance update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> updateEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedEncumbrance = (Transaction)(updateEntities.get(0).get(0));
          assertThat(savedEncumbrance.getTransactionType(), equalTo(ENCUMBRANCE));
          assertNotNull(savedEncumbrance.getMetadata().getUpdatedDate());
          assertThat(savedEncumbrance.getAmount(), equalTo(0d));
          assertThat(savedEncumbrance.getEncumbrance().getStatus(), equalTo(RELEASED));
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(5d));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(5d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testUpdatePendingPaymentWithLinkedEncumbrance(VertxTestContext testContext) {
    String pendingPaymentId = UUID.randomUUID().toString();
    String encumbranceId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

    Transaction existingPendingPayment = new Transaction()
      .withId(pendingPaymentId)
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(5d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withAwaitingPayment(new AwaitingPayment()
        .withEncumbranceId(encumbranceId)
        .withReleaseEncumbrance(true))
      .withMetadata(new Metadata());

    Transaction newPendingPayment = JsonObject.mapFrom(existingPendingPayment).mapTo(Transaction.class);
    newPendingPayment.setAmount(10d);

    Transaction existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withAmount(0d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(RELEASED)
        .withAmountAwaitingPayment(5d)
        .withInitialAmountEncumbered(5d))
      .withMetadata(new Metadata());

    Batch batch = new Batch();
    batch.getTransactionsToUpdate().add(newPendingPayment);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 0d, false, false);

    Criterion pendingPaymentCriterion = createCriterionByIds(List.of(pendingPaymentId));
    doReturn(succeededFuture(createResults(List.of(existingPendingPayment))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(pendingPaymentCriterion.toString())));

    Criterion encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(encumbranceCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify pending payment update
          ArgumentCaptor<String> tableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> entitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(2)).updateBatch(tableNamesCaptor.capture(), entitiesCaptor.capture());
          List<String> tableNames = tableNamesCaptor.getAllValues();
          List<List<Object>> entities = entitiesCaptor.getAllValues();
          assertThat(tableNames.get(0), equalTo(TRANSACTIONS_TABLE));

          Transaction savedPendingPayment = (Transaction)(entities.get(0).get(0));
          assertThat(savedPendingPayment.getTransactionType(), equalTo(PENDING_PAYMENT));
          assertNotNull(savedPendingPayment.getMetadata().getUpdatedDate());
          assertThat(savedPendingPayment.getAmount(), equalTo(10d));

          // Verify encumbrance update
          Transaction savedEncumbrance = (Transaction)(entities.get(0).get(1));
          assertThat(savedEncumbrance.getTransactionType(), equalTo(ENCUMBRANCE));
          assertThat(savedEncumbrance.getEncumbrance().getStatus(), equalTo(RELEASED));
          assertNotNull(savedEncumbrance.getMetadata().getUpdatedDate());
          assertThat(savedEncumbrance.getAmount(), equalTo(0d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(10d));

          // Verify budget update
          assertThat(tableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(entities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(10d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCancelPendingPaymentWithLinkedEncumbrance(VertxTestContext testContext) {
    String pendingPaymentId = UUID.randomUUID().toString();
    String encumbranceId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

    Transaction existingPendingPayment = new Transaction()
      .withId(pendingPaymentId)
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(5d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withAwaitingPayment(new AwaitingPayment()
        .withEncumbranceId(encumbranceId)
        .withReleaseEncumbrance(true))
      .withMetadata(new Metadata());

    Transaction newPendingPayment = JsonObject.mapFrom(existingPendingPayment).mapTo(Transaction.class);
    newPendingPayment.setInvoiceCancelled(true);

    Transaction existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withAmount(0d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(RELEASED)
        .withAmountAwaitingPayment(5d)
        .withInitialAmountEncumbered(5d))
      .withMetadata(new Metadata());

    Batch batch = new Batch();
    batch.getTransactionsToUpdate().add(newPendingPayment);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 0d, false, false);

    Criterion pendingPaymentCriterion = createCriterionByIds(List.of(pendingPaymentId));
    doReturn(succeededFuture(createResults(List.of(existingPendingPayment))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(pendingPaymentCriterion.toString())));

    Criterion encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(encumbranceCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify pending payment update
          ArgumentCaptor<String> tableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> entitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(2)).updateBatch(tableNamesCaptor.capture(), entitiesCaptor.capture());
          List<String> tableNames = tableNamesCaptor.getAllValues();
          List<List<Object>> entities = entitiesCaptor.getAllValues();
          assertThat(tableNames.get(0), equalTo(TRANSACTIONS_TABLE));

          Transaction savedPendingPayment = (Transaction)(entities.get(0).get(0));
          assertThat(savedPendingPayment.getTransactionType(), equalTo(PENDING_PAYMENT));
          assertNotNull(savedPendingPayment.getMetadata().getUpdatedDate());
          assertThat(savedPendingPayment.getAmount(), equalTo(0d));
          assertThat(savedPendingPayment.getVoidedAmount(), equalTo(5d));

          // Verify encumbrance update
          Transaction savedEncumbrance = (Transaction)(entities.get(0).get(1));
          assertThat(savedEncumbrance.getTransactionType(), equalTo(ENCUMBRANCE));
          assertThat(savedEncumbrance.getEncumbrance().getStatus(), equalTo(RELEASED));
          assertNotNull(savedEncumbrance.getMetadata().getUpdatedDate());
          assertThat(savedEncumbrance.getAmount(), equalTo(0d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(0d));

          // Verify budget update
          assertThat(tableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(entities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(0d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreatePaymentWithLinkedEncumbrance(VertxTestContext testContext) {
    String paymentId = UUID.randomUUID().toString();
    String pendingPaymentId = UUID.randomUUID().toString();
    String encumbranceId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

    Transaction payment = new Transaction()
      .withId(paymentId)
      .withTransactionType(PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(5d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withPaymentEncumbranceId(encumbranceId);

    Transaction existingPendingPayment = new Transaction()
      .withId(pendingPaymentId)
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(5d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withAwaitingPayment(new AwaitingPayment()
        .withEncumbranceId(encumbranceId)
        .withReleaseEncumbrance(true));

    Transaction existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withAmount(0d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(RELEASED)
        .withAmountAwaitingPayment(5d)
        .withInitialAmountEncumbered(5d))
      .withMetadata(new Metadata());

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(payment);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 0d, false, false);

    Criterion paymentCriterion = createCriterionByIds(List.of(paymentId));
    doReturn(succeededFuture(createResults(List.of())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(paymentCriterion.toString())));

    Criterion encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(encumbranceCriterion.toString())));

    String snippet = "WHERE (jsonb->>'transactionType') = 'Pending payment'  AND  (  (jsonb->>'sourceInvoiceId') = '" + invoiceId + "')    ";
    doReturn(succeededFuture(createResults(List.of(existingPendingPayment))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(snippet)));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).saveBatch(anyString(), anyList());

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    doAnswer(invocation -> succeededFuture(createRowSet(List.of(existingPendingPayment))))
      .when(conn).delete(anyString(), any(Criterion.class));

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify payment creation
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> saveEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();

          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedPayment = (Transaction)(saveEntities.get(0).get(0));
          assertThat(savedPayment.getTransactionType(), equalTo(PAYMENT));
          assertNotNull(savedPayment.getMetadata().getCreatedDate());
          assertThat(savedPayment.getAmount(), equalTo(5d));

          // Verify encumbrance update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> updateEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedEncumbrance = (Transaction)(updateEntities.get(0).get(0));
          assertThat(savedEncumbrance.getTransactionType(), equalTo(ENCUMBRANCE));
          assertNotNull(savedEncumbrance.getMetadata().getUpdatedDate());
          assertThat(savedEncumbrance.getAmount(), equalTo(0d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(0d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountExpended(), equalTo(5d));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(0d));
          assertThat(savedBudget.getExpenditures(), equalTo(5d));

          // Verify pending payment deletion
          ArgumentCaptor<String> deleteTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<Criterion> deleteCriterionCaptor = ArgumentCaptor.forClass(Criterion.class);
          verify(conn, times(1)).delete(deleteTableNamesCaptor.capture(), deleteCriterionCaptor.capture());
          List<String> deleteTableNames = deleteTableNamesCaptor.getAllValues();
          List<Criterion> deleteCriterions = deleteCriterionCaptor.getAllValues();

          Criterion pendingPaymentCriterionByIds = createCriterionByIds(List.of(pendingPaymentId));
          assertThat(deleteTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Criterion deleteCriterion = deleteCriterions.get(0);
          assertThat(deleteCriterion.toString(), equalTo(pendingPaymentCriterionByIds.toString()));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCancelPaymentWithLinkedEncumbrance(VertxTestContext testContext) {
    String paymentId = UUID.randomUUID().toString();
    String encumbranceId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

    Transaction existingPayment = new Transaction()
      .withId(paymentId)
      .withTransactionType(PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(5d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withPaymentEncumbranceId(encumbranceId)
      .withMetadata(new Metadata());

    Transaction newPayment = JsonObject.mapFrom(existingPayment).mapTo(Transaction.class);
    newPayment.setInvoiceCancelled(true);

    Transaction existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withAmount(0d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(RELEASED)
        .withAmountAwaitingPayment(0d)
        .withAmountExpended(5d)
        .withInitialAmountEncumbered(5d))
      .withMetadata(new Metadata());

    Batch batch = new Batch();
    batch.getTransactionsToUpdate().add(newPayment);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 5d, false, false);

    Criterion paymentCriterion = createCriterionByIds(List.of(paymentId));
    doReturn(succeededFuture(createResults(List.of(existingPayment))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(paymentCriterion.toString())));

    Criterion encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(encumbranceCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).saveBatch(anyString(), anyList());

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify payment update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> updateEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedPayment = (Transaction)(updateEntities.get(0).get(0));
          assertThat(savedPayment.getTransactionType(), equalTo(PAYMENT));
          assertNotNull(savedPayment.getMetadata().getUpdatedDate());
          assertThat(savedPayment.getAmount(), equalTo(0d));
          assertThat(savedPayment.getVoidedAmount(), equalTo(5d));

          // Verify encumbrance update
          assertThat(updateTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedEncumbrance = (Transaction)(updateEntities.get(0).get(1));
          assertThat(savedEncumbrance.getTransactionType(), equalTo(ENCUMBRANCE));
          assertThat(savedEncumbrance.getEncumbrance().getStatus(), equalTo(RELEASED));
          assertNotNull(savedEncumbrance.getMetadata().getUpdatedDate());
          assertThat(savedEncumbrance.getAmount(), equalTo(5d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(0d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountExpended(), equalTo(0d));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(0d));
          assertThat(savedBudget.getExpenditures(), equalTo(0d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testDeleteTransaction(VertxTestContext testContext) {
    String tenantId = "tenantname";
    String encumbranceId = UUID.randomUUID().toString();
    Transaction transaction = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withEncumbrance(new Encumbrance()
        .withStatus(RELEASED));

    Batch batch = new Batch();
    batch.getIdsOfTransactionsToDelete().add(encumbranceId);

    doReturn(tenantId)
      .when(conn).getTenantId();

    Criterion criterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(transaction))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(criterion.toString())));

    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    criterionBuilder.withJson("awaitingPayment.encumbranceId", "=", encumbranceId);
    doReturn(succeededFuture(createResults(List.of())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(criterionBuilder.build().toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(List.of(transaction))))
      .when(conn).delete(anyString(), any(Criterion.class));

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify transaction deletion
          ArgumentCaptor<String> deleteTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<Criterion> deleteCriterionCaptor = ArgumentCaptor.forClass(Criterion.class);
          verify(conn, times(1)).delete(deleteTableNamesCaptor.capture(), deleteCriterionCaptor.capture());
          List<String> deleteTableNames = deleteTableNamesCaptor.getAllValues();
          List<Criterion> deleteCriterions = deleteCriterionCaptor.getAllValues();

          Criterion expectedCriterion = createCriterionByIds(List.of(encumbranceId));
          assertThat(deleteTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Criterion deleteCriterion = deleteCriterions.get(0);
          assertThat(deleteCriterion.toString(), equalTo(expectedCriterion.toString()));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testPatchTransaction(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    TransactionPatch transactionPatch = new TransactionPatch()
      .withId(transactionId)
      .withAdditionalProperty("encumbrance", new LinkedHashMap<String, String>()
        .put("orderStatus", Encumbrance.OrderStatus.CLOSED.value()));
    Batch batch = new Batch();
    batch.getTransactionPatches().add(transactionPatch);
    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onFailure(thrown -> {
        testContext.verify(() -> {
          assertThat(thrown, instanceOf(HttpException.class));
          assertThat(((HttpException) thrown).getCode(), equalTo(500));
          assertThat(thrown.getMessage(), equalTo("transactionPatches: not implemented"));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreateInitialAllocation(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();

    Transaction allocation = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withToFundId(fundId)
      .withTransactionType(ALLOCATION)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId);

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(allocation);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 0d, false, false);

    Criterion transactionCriterion = createCriterionByIds(List.of(transactionId));
    doReturn(succeededFuture(createResults(new ArrayList<Transaction>())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).saveBatch(anyString(), anyList());

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify allocation creation
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> saveEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();
          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(saveEntities.get(0).get(0));
          assertNotNull(savedTransaction.getMetadata());
          assertThat(savedTransaction.getAmount(), equalTo(allocation.getAmount()));

          // Verify budget update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> updateEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(1)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          assertThat(updateTableNames.get(0), equalTo(BUDGET_TABLE));
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();
          Budget savedBudget = (Budget)(updateEntities.get(0).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getInitialAllocation(), equalTo(10d));
          assertThat(savedBudget.getAllocationTo(), equalTo(5d));

        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreateAllocation(VertxTestContext testContext) {
    String fundId1 = UUID.randomUUID().toString();
    String fundId2 = UUID.randomUUID().toString();
    String budgetId1 = UUID.randomUUID().toString();
    String budgetId2 = UUID.randomUUID().toString();
    String transactionId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();

    Transaction allocation = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId1)
      .withToFundId(fundId2)
      .withTransactionType(ALLOCATION)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId);

    setup2Funds2Budgets1Ledger(fundId1, fundId2, budgetId1, budgetId2, fiscalYearId);

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(allocation);

    Criterion transactionCriterion = createCriterionByIds(List.of(transactionId));
    doReturn(succeededFuture(createResults(new ArrayList<Transaction>())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).saveBatch(anyString(), anyList());

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify allocation creation
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> saveEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();
          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(saveEntities.get(0).get(0));
          assertNotNull(savedTransaction.getMetadata());
          assertThat(savedTransaction.getAmount(), equalTo(allocation.getAmount()));

          // Verify budget updates
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> updateEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(1)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          assertThat(updateTableNames.get(0), equalTo(BUDGET_TABLE));
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();
          Budget savedBudget1 = (Budget)(updateEntities.get(0).get(0));
          assertThat(savedBudget1.getId(), equalTo(budgetId1));
          assertNotNull(savedBudget1.getMetadata().getUpdatedDate());
          assertThat(savedBudget1.getAllocationFrom(), equalTo(5d));
          Budget savedBudget2 = (Budget)(updateEntities.get(0).get(1));
          assertThat(savedBudget2.getId(), equalTo(budgetId2));
          assertNotNull(savedBudget2.getMetadata().getUpdatedDate());
          assertThat(savedBudget2.getAllocationTo(), equalTo(5d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreateTransfer(VertxTestContext testContext) {
    String fundId1 = UUID.randomUUID().toString();
    String fundId2 = UUID.randomUUID().toString();
    String budgetId1 = UUID.randomUUID().toString();
    String budgetId2 = UUID.randomUUID().toString();
    String transactionId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();

    Transaction transfer = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId1)
      .withToFundId(fundId2)
      .withTransactionType(TRANSFER)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId);

    setup2Funds2Budgets1Ledger(fundId1, fundId2, budgetId1, budgetId2, fiscalYearId);

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(transfer);

    Criterion transactionCriterion = createCriterionByIds(List.of(transactionId));
    doReturn(succeededFuture(createResults(new ArrayList<Transaction>())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).saveBatch(anyString(), anyList());

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify allocation creation
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> saveEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();
          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(saveEntities.get(0).get(0));
          assertNotNull(savedTransaction.getMetadata());
          assertThat(savedTransaction.getAmount(), equalTo(transfer.getAmount()));

          // Verify budget updates
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<List<Object>> updateEntitiesCaptor = ArgumentCaptor.forClass(List.class);
          verify(conn, times(1)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          assertThat(updateTableNames.get(0), equalTo(BUDGET_TABLE));
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();
          Budget savedBudget1 = (Budget)(updateEntities.get(0).get(0));
          assertThat(savedBudget1.getId(), equalTo(budgetId1));
          assertNotNull(savedBudget1.getMetadata().getUpdatedDate());
          assertThat(savedBudget1.getNetTransfers(), equalTo(-5d));
          Budget savedBudget2 = (Budget)(updateEntities.get(0).get(1));
          assertThat(savedBudget2.getId(), equalTo(budgetId2));
          assertNotNull(savedBudget2.getMetadata().getUpdatedDate());
          assertThat(savedBudget2.getNetTransfers(), equalTo(5d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testExpenditureRestrictionsWhenCreatingTwoPendingPayments(VertxTestContext testContext) {
    String pendingPaymentId1 = UUID.randomUUID().toString();
    String pendingPaymentId2 = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

    Transaction pendingPayment1 = new Transaction()
      .withId(pendingPaymentId1)
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(7d)
      .withCurrency(currency)
      .withInvoiceCancelled(false);

    Transaction pendingPayment2 = new Transaction()
      .withId(pendingPaymentId2)
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(8d)
      .withCurrency(currency)
      .withInvoiceCancelled(false);

    Batch batch = new Batch();
    batch.getTransactionsToCreate().addAll(List.of(pendingPayment1, pendingPayment2));

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 0d, true, false);

    Criterion pendingPaymentCriterion = createCriterionByIds(List.of(pendingPaymentId1, pendingPaymentId2));
    doReturn(succeededFuture(createResults(List.of())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(pendingPaymentCriterion.toString())));

    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onFailure(thrown -> {
        HttpException exception = (HttpException)thrown;
        testContext.verify(() -> {
          assertThat(exception.getCode(), equalTo(422));
          assertThat(exception.getErrors().getErrors().get(0).getMessage(),
            equalTo(BUDGET_RESTRICTED_EXPENDITURES_ERROR.getDescription()));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testEncumbranceRestrictionsWhenCreatingTwoEncumbrances(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction encumbrance = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(10d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(Encumbrance.Status.PENDING)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(10d));

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(encumbrance);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 0d, false, true);

    Criterion transactionCriterion = createCriterionByIds(List.of(transactionId));
    doReturn(succeededFuture(createResults(new ArrayList<Transaction>())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onFailure(thrown -> {
        HttpException exception = (HttpException)thrown;
        testContext.verify(() -> {
          assertThat(exception.getCode(), equalTo(422));
          assertThat(exception.getErrors().getErrors().get(0).getMessage(),
            equalTo(BUDGET_RESTRICTED_ENCUMBRANCE_ERROR.getDescription()));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreatePaymentWithNegativeAmount(VertxTestContext testContext) {
    String paymentId = UUID.randomUUID().toString();
    String encumbranceId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

    Transaction payment = new Transaction()
      .withId(paymentId)
      .withTransactionType(PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(-5d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withPaymentEncumbranceId(encumbranceId);

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(payment);

    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertThat(event.cause(), instanceOf(HttpException.class));
          assertThat(((HttpException) event.cause()).getCode(), equalTo(422));
        });
        testContext.completeNow();
      });
  }

  private void setupFundBudgetLedger(String fundId, String fiscalYearId, double budgetEncumbered,
      double budgetAwaitingPayment, double budgetExpenditures, boolean restrictExpenditures, boolean restrictEncumbrance) {
    String tenantId = "tenantname";
    String budgetId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();

    Fund fund = new Fund()
      .withId(fundId)
      .withLedgerId(ledgerId);

    Budget budget = new Budget()
      .withId(budgetId)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withBudgetStatus(ACTIVE)
      .withInitialAllocation(10d)
      .withEncumbered(budgetEncumbered)
      .withAwaitingPayment(budgetAwaitingPayment)
      .withExpenditures(budgetExpenditures)
      .withAllowableExpenditure(90d)
      .withAllowableEncumbrance(90d)
      .withMetadata(new Metadata());

    Ledger ledger = new Ledger()
      .withId(ledgerId)
      .withRestrictExpenditures(restrictExpenditures)
      .withRestrictEncumbrance(restrictEncumbrance);

    doReturn(tenantId)
      .when(conn).getTenantId();

    Criterion fundCriterion = createCriterionByIds(List.of(fundId));
    doReturn(succeededFuture(createResults(List.of(fund))))
      .when(conn).get(eq(FUND_TABLE), eq(Fund.class), argThat(
        crit -> crit.toString().equals(fundCriterion.toString())), eq(false));

    String sql = "SELECT jsonb FROM " + tenantId + "_mod_finance_storage.budget WHERE (fiscalYearId = '" + fiscalYearId + "' AND (fundId = '" + fundId + "')) FOR UPDATE";
    doReturn(succeededFuture(createRowSet(List.of(budget))))
      .when(conn).execute(eq(sql), any(Tuple.class));

    Criterion ledgerCriterion = createCriterionByIds(List.of(ledgerId));
    doReturn(succeededFuture(createResults(List.of(ledger))))
      .when(conn).get(eq(LEDGER_TABLE), eq(Ledger.class), argThat(
        crit -> crit.toString().equals(ledgerCriterion.toString())), eq(false));
  }

  private void setup2Funds2Budgets1Ledger(String fundId1, String fundId2, String budgetId1, String budgetId2,
      String fiscalYearId) {
    String tenantId = "tenantname";
    String ledgerId = UUID.randomUUID().toString();
    Ledger ledger = new Ledger()
      .withId(ledgerId);

    Fund fund1 = new Fund()
      .withId(fundId1)
      .withLedgerId(ledgerId);

    Fund fund2 = new Fund()
      .withId(fundId2)
      .withLedgerId(ledgerId);

    Budget budget1 = new Budget()
      .withId(budgetId1)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId1)
      .withBudgetStatus(ACTIVE)
      .withInitialAllocation(5d)
      .withNetTransfers(0d)
      .withMetadata(new Metadata());

    Budget budget2 = new Budget()
      .withId(budgetId2)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId2)
      .withBudgetStatus(ACTIVE)
      .withInitialAllocation(10d)
      .withNetTransfers(0d)
      .withMetadata(new Metadata());

    doReturn(tenantId)
      .when(conn).getTenantId();

    Criterion fundCriterion = createCriterionByIds(List.of(fundId1, fundId2));
    doReturn(succeededFuture(createResults(List.of(fund1, fund2))))
      .when(conn).get(eq(FUND_TABLE), eq(Fund.class), argThat(
        crit -> crit.toString().equals(fundCriterion.toString())), eq(false));

    String sql = "SELECT jsonb FROM " + tenantId + "_mod_finance_storage.budget WHERE (fiscalYearId = '" + fiscalYearId + "' AND (fundId = '%s' OR fundId = '%s')) FOR UPDATE";
    doReturn(succeededFuture(createRowSet(List.of(budget1, budget2))))
      .when(conn).execute(argThat(s ->
          s.equals(String.format(sql, fundId1, fundId2)) || s.equals(String.format(sql, fundId2, fundId1))),
        any(Tuple.class));

    Criterion ledgerCriterion = createCriterionByIds(List.of(ledgerId));
    doReturn(succeededFuture(createResults(List.of(ledger))))
      .when(conn).get(eq(LEDGER_TABLE), eq(Ledger.class), argThat(
        crit -> crit.toString().equals(ledgerCriterion.toString())), eq(false));
  }

  private Criterion createCriterionByIds(List<String> ids) {
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    ids.forEach(id -> criterionBuilder.with("id", id));
    return criterionBuilder.build();
  }

  private <T> Results<T> createResults(List<T> list) {
    Results<T> results = new Results<>();
    results.setResults(list);
    return results;
  }

  private <T> RowSet<Row> createRowSet(List<T> list) {
    RowDesc rowDesc = new LocalRowDesc(List.of("foo"));
    List<Row> rows = list.stream().map(item -> {
      Row row = new RowImpl(rowDesc);
      row.addJsonObject(JsonObject.mapFrom(item));
      return row;
    }).toList();
    return new LocalRowSet(list.size())
      .withRows(rows);
  }

}
