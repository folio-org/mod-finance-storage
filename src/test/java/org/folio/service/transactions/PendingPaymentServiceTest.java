package org.folio.service.transactions;

import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.service.transactions.PendingPaymentService.SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.folio.dao.transactions.EncumbranceDAO;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.AwaitingPayment;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.HelperUtils;
import org.folio.service.budget.BudgetService;
import org.folio.service.transactions.cancel.CancelTransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

public class PendingPaymentServiceTest {

  private static final String TENANT_ID = "tenant";

  private AutoCloseable mockitoMocks;

  @InjectMocks
  private PendingPaymentService pendingPaymentService;

  @Mock
  private BudgetService budgetService;
  @Mock
  private CancelTransactionService cancelTransactionService;
  @Mock
  private EncumbranceDAO transactionsDAO;
  @Mock
  private DBConn conn;

  private final String summaryId = UUID.randomUUID().toString();
  private final String encumbranceId = UUID.randomUUID().toString();
  private final String fundId = UUID.randomUUID().toString();
  private final String fiscalYearId = UUID.randomUUID().toString();
  private final String currency = "USD";
  private Transaction linkedTransaction;
  private Transaction notLinkedTransaction;
  private Transaction encumbrance;
  private Budget budget;


  @BeforeEach
  public void initMocks(){
    mockitoMocks = MockitoAnnotations.openMocks(this);
    when(conn.getTenantId()).thenReturn(TENANT_ID);
  }

  @BeforeEach
  public void prepareData(){

    linkedTransaction = new Transaction()
      .withAwaitingPayment(new AwaitingPayment().withEncumbranceId(encumbranceId).withReleaseEncumbrance(false))
      .withSourceInvoiceId(summaryId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withCurrency(currency)
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT);

    notLinkedTransaction = new Transaction()
      .withSourceInvoiceId(summaryId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withCurrency(currency)
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT);

    encumbrance = new Transaction()
      .withId(encumbranceId)
      .withCurrency(currency)
      .withFromFundId(fundId)
      .withTransactionType(Transaction.TransactionType.ENCUMBRANCE)
      .withEncumbrance(new Encumbrance()
        .withInitialAmountEncumbered(10d));

    budget = new Budget()
      .withFiscalYearId(fiscalYearId)
      .withAwaitingPayment(0d)
      .withAllocated(100d)
      .withAvailable(100d)
      .withEncumbered(0d)
      .withUnavailable(0d)
      .withFundId(fundId);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void testProcessTemporaryToPermanentTransactionsWithLinkedAndNotLinkedPendingPayments() {
    BigDecimal linkedAmount = BigDecimal.valueOf(9.1);
    linkedTransaction.withAmount(linkedAmount.doubleValue());
    BigDecimal notLinkedAmount = BigDecimal.valueOf(1.5);
    notLinkedTransaction.withAmount(notLinkedAmount.doubleValue());

    List<Transaction> transactions = Arrays.asList(linkedTransaction, notLinkedTransaction);

    BigDecimal awaitingPayment = BigDecimal.ZERO;
    BigDecimal available = BigDecimal.valueOf(90d);
    BigDecimal encumbered = BigDecimal.valueOf(10d);
    BigDecimal unavailable = BigDecimal.valueOf(10d);
    budget.withAwaitingPayment(awaitingPayment.doubleValue())
      .withAvailable(available.doubleValue())
      .withEncumbered(encumbered.doubleValue())
      .withUnavailable(unavailable.doubleValue());
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(conn)))
      .thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    encumbrance.withAmount(encumbered.doubleValue());
    List<Transaction> encumbrances = Collections.singletonList(encumbrance);

    when(transactionsDAO.getTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture(encumbrances));

    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);


    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    Future<Void> result = spyService.createTransactions(transactions, conn);

    assertTrue(result.succeeded());

    final ArgumentCaptor<List<String>> idsArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).getTransactions(idsArgumentCaptor.capture(), eq(conn));
    final List<String> ids = idsArgumentCaptor.getValue();
    assertThat(ids, contains(encumbranceId));

    final ArgumentCaptor<List<Transaction>> encumbranceUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).updatePermanentTransactions(encumbranceUpdateArgumentCapture.capture(), eq(conn));
    Transaction updatedEncumbrance = encumbranceUpdateArgumentCapture.getValue().get(0);
    assertThat(updatedEncumbrance.getAmount(), is(encumbered.subtract(linkedAmount).doubleValue()));
    assertThat(updatedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), is(BigDecimal.ZERO.add(linkedAmount).doubleValue()));

    verify(transactionsDAO).saveTransactionsToPermanentTable(eq(summaryId), eq(conn));

    String budgetTableName = HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE);
    String transactionTableName = HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS);
    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(conn));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(conn));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    assertThat(updatedBudget.getAvailable(), is(available.doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(linkedAmount).add(notLinkedAmount).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(encumbered.subtract(linkedAmount).doubleValue()));


  }

  @Test
  void testProcessTemporaryToPermanentTransactionsWithLinkedPendingPaymentReleaseEncumbrance() {
    BigDecimal linkedAmount = BigDecimal.valueOf(9.1);
    linkedTransaction.withAmount(linkedAmount.doubleValue());
    linkedTransaction.getAwaitingPayment().setReleaseEncumbrance(true);

    List<Transaction> transactions = Collections.singletonList(linkedTransaction);

    BigDecimal awaitingPayment = BigDecimal.ONE;
    BigDecimal available = BigDecimal.valueOf(90d);
    BigDecimal encumbered = BigDecimal.valueOf(9d);
    BigDecimal unavailable = BigDecimal.valueOf(10d);
    budget.withAwaitingPayment(awaitingPayment.doubleValue())
      .withAvailable(available.doubleValue())
      .withEncumbered(encumbered.doubleValue())
      .withUnavailable(unavailable.doubleValue());
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(conn)))
      .thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    encumbrance.withAmount(encumbered.doubleValue());
    encumbrance.getEncumbrance().setAmountAwaitingPayment(awaitingPayment.doubleValue());
    List<Transaction> encumbrances = Collections.singletonList(encumbrance);

    when(transactionsDAO.getTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture(encumbrances));

    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    Future<Void> result = spyService.createTransactions(transactions, conn);

    assertTrue(result.succeeded());

    final ArgumentCaptor<List<String>> idsArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).getTransactions(idsArgumentCaptor.capture(), eq(conn));
    final List<String> ids = idsArgumentCaptor.getValue();
    assertThat(ids, contains(encumbranceId));

    final ArgumentCaptor<List<Transaction>> encumbranceUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).updatePermanentTransactions(encumbranceUpdateArgumentCapture.capture(), eq(conn));
    Transaction updatedEncumbrance = encumbranceUpdateArgumentCapture.getValue().get(0);
    assertThat(updatedEncumbrance.getAmount(), is(0.0));
    assertThat(updatedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), is(10.1d));

    verify(transactionsDAO).saveTransactionsToPermanentTable(eq(summaryId), eq(conn));

    String budgetTableName = HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE);
    String transactionTableName = HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS);
    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(conn));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(conn));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    assertThat(updatedBudget.getAvailable(), is(available.doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(linkedAmount).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(0.0));

  }

  @Test
  void testProcessTemporaryToPermanentTransactionsWithLinkedPendingPaymentGreaterThanEncumbrance() {
    BigDecimal linkedAmount = BigDecimal.valueOf(11d);
    linkedTransaction.withAmount(linkedAmount.doubleValue());
    linkedTransaction.getAwaitingPayment().setReleaseEncumbrance(false);

    List<Transaction> transactions = Collections.singletonList(linkedTransaction);

    BigDecimal awaitingPayment = BigDecimal.ZERO;
    BigDecimal available = BigDecimal.valueOf(90d);
    BigDecimal encumbered = BigDecimal.valueOf(10d);
    BigDecimal unavailable = BigDecimal.valueOf(10d);
    budget.withAwaitingPayment(awaitingPayment.doubleValue())
      .withAvailable(available.doubleValue())
      .withEncumbered(encumbered.doubleValue())
      .withUnavailable(unavailable.doubleValue());
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(conn)))
      .thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    encumbrance.withAmount(encumbered.doubleValue());
    List<Transaction> encumbrances = Collections.singletonList(encumbrance);

    when(transactionsDAO.getTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture(encumbrances));

    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    Future<Void> result = spyService.createTransactions(transactions, conn);

    assertTrue(result.succeeded());

    final ArgumentCaptor<List<String>> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).getTransactions(listArgumentCaptor.capture(), eq(conn));
    final List<String> idsArgument = listArgumentCaptor.getValue();
    assertThat(idsArgument, contains(linkedTransaction.getAwaitingPayment().getEncumbranceId()));

    final ArgumentCaptor<List<Transaction>> encumbranceUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).updatePermanentTransactions(encumbranceUpdateArgumentCapture.capture(), eq(conn));
    Transaction updatedEncumbrance = encumbranceUpdateArgumentCapture.getValue().get(0);
    assertThat(updatedEncumbrance.getAmount(), is(0.0));
    assertThat(updatedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), is(linkedAmount.doubleValue()));

    verify(transactionsDAO).saveTransactionsToPermanentTable(eq(summaryId), eq(conn));

    String budgetTableName = HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE);
    String transactionTableName = HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS);
    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(conn));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(conn));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    assertThat(updatedBudget.getAvailable(), is(available.doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(linkedAmount).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(0d));

  }

  @Test
  void testProcessTemporaryToPermanentTransactionsWithLinkedPendingPaymentGreaterThanBudgetRemainingAmount() {
    BigDecimal linkedAmount = BigDecimal.valueOf(110d);
    linkedTransaction.withAmount(linkedAmount.doubleValue());
    linkedTransaction.getAwaitingPayment().setReleaseEncumbrance(false);

    List<Transaction> transactions = Collections.singletonList(linkedTransaction);

    BigDecimal awaitingPayment = BigDecimal.ZERO;
    BigDecimal allocated = BigDecimal.valueOf(50d);
    BigDecimal netTransfer = BigDecimal.valueOf(50d);
    BigDecimal available = BigDecimal.valueOf(90d);
    BigDecimal encumbered = BigDecimal.valueOf(10d);
    BigDecimal unavailable = BigDecimal.valueOf(10d);
    budget.withAwaitingPayment(awaitingPayment.doubleValue())
      .withAvailable(available.doubleValue())
      .withEncumbered(encumbered.doubleValue())
      .withUnavailable(unavailable.doubleValue());
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(conn)))
      .thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    encumbrance.withAmount(encumbered.doubleValue());
    List<Transaction> encumbrances = Collections.singletonList(encumbrance);

    when(transactionsDAO.getTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture(encumbrances));

    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    Future<Void> result = spyService.createTransactions(transactions, conn);

    assertTrue(result.succeeded());

    final ArgumentCaptor<List<String>> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).getTransactions(listArgumentCaptor.capture(), eq(conn));
    final List<String> idsArgument = listArgumentCaptor.getValue();
    assertThat(idsArgument, contains(linkedTransaction.getAwaitingPayment().getEncumbranceId()));

    final ArgumentCaptor<List<Transaction>> encumbranceUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).updatePermanentTransactions(encumbranceUpdateArgumentCapture.capture(), eq(conn));
    Transaction updatedEncumbrance = encumbranceUpdateArgumentCapture.getValue().get(0);
    assertThat(updatedEncumbrance.getAmount(), is(0.0));
    assertThat(updatedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), is(linkedAmount.doubleValue()));

    verify(transactionsDAO).saveTransactionsToPermanentTable(eq(summaryId), eq(conn));

    String budgetTableName = HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE);
    String transactionTableName = HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS);
    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(conn));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(conn));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    assertThat(updatedBudget.getAvailable(), is(available.doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(linkedAmount).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(0d));

  }

  @Test
  void testProcessTemporaryToPermanentTransactionsWithNotLinkedPendingPayment() {
    BigDecimal notLinkedAmount = BigDecimal.valueOf(1.5);
    notLinkedTransaction.withAmount(notLinkedAmount.doubleValue());

    List<Transaction> transactions = Collections.singletonList(notLinkedTransaction);

    BigDecimal awaitingPayment = BigDecimal.ZERO;
    BigDecimal available = BigDecimal.valueOf(90d);
    BigDecimal encumbered = BigDecimal.valueOf(10d);
    BigDecimal unavailable = BigDecimal.valueOf(10d);
    budget.withAwaitingPayment(awaitingPayment.doubleValue())
      .withAvailable(available.doubleValue())
      .withEncumbered(encumbered.doubleValue())
      .withUnavailable(unavailable.doubleValue());
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(conn)))
      .thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    spyService.createTransactions(transactions, conn)
      .onComplete(res -> assertTrue(res.succeeded()));


    verify(transactionsDAO, never()).getTransactions(anyList(), any());
    verify(transactionsDAO, never()).updatePermanentTransactions(anyList(), any());

    verify(transactionsDAO).saveTransactionsToPermanentTable(eq(summaryId), eq(conn));

    String budgetTableName = HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE);
    String transactionTableName = HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS);
    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(conn));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(conn));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    assertThat(updatedBudget.getAvailable(), is(available.doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(notLinkedAmount).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(encumbered.doubleValue()));

  }


  @Test
  void overExpendedShouldBeIncreasedWhenProcessTemporaryToPermanentTransactionsWithNotLinkedPendingPaymentWithAmountGreaterThenBudgetRemaining() {
    BigDecimal notLinkedAmount = BigDecimal.valueOf(150);
    notLinkedTransaction.withAmount(notLinkedAmount.doubleValue());

    List<Transaction> transactions = Collections.singletonList(notLinkedTransaction);

    BigDecimal awaitingPayment = BigDecimal.ZERO;
    BigDecimal available = BigDecimal.valueOf(90d);
    BigDecimal encumbered = BigDecimal.valueOf(10d);
    BigDecimal unavailable = BigDecimal.valueOf(10d);
    budget.withAwaitingPayment(awaitingPayment.doubleValue())
      .withAvailable(available.doubleValue())
      .withEncumbered(encumbered.doubleValue())
      .withUnavailable(unavailable.doubleValue());
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(conn)))
      .thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    spyService.createTransactions(transactions, conn)
      .onComplete(res -> assertTrue(res.succeeded()));


    verify(transactionsDAO, never()).getTransactions(anyList(), any());
    verify(transactionsDAO, never()).updatePermanentTransactions(anyList(), any());

    verify(transactionsDAO).saveTransactionsToPermanentTable(eq(summaryId), eq(conn));

    String budgetTableName = HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE);
    String transactionTableName = HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS);
    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(conn));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(conn));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    assertThat(updatedBudget.getAvailable(), is(available.doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(notLinkedAmount).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(encumbered.doubleValue()));

  }

  @Test
  void shouldUpdateBudgetsTotalsWhenUpdateNotLinkedToEncumbrancePendingPaymentsAmount() {
    BigDecimal newAmount = BigDecimal.valueOf(1.5);
    BigDecimal amount = BigDecimal.valueOf(2d);
    notLinkedTransaction.withAmount(newAmount.doubleValue());
    Transaction existingTransaction = JsonObject.mapFrom(notLinkedTransaction).mapTo(Transaction.class)
      .withAmount(amount.doubleValue());
    List<Transaction> transactions = Collections.singletonList(notLinkedTransaction);

    BigDecimal awaitingPayment = BigDecimal.ZERO;
    BigDecimal available = BigDecimal.valueOf(90d);
    BigDecimal encumbered = BigDecimal.valueOf(10d);
    BigDecimal unavailable = BigDecimal.valueOf(10d);
    budget.withAwaitingPayment(awaitingPayment.doubleValue())
      .withAvailable(available.doubleValue())
      .withEncumbered(encumbered.doubleValue())
      .withUnavailable(unavailable.doubleValue());
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(conn)))
      .thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(transactionsDAO.getTransactions(anyList(), any()))
      .thenReturn(Future.succeededFuture(Collections.singletonList(existingTransaction)));

    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    spyService.cancelAndUpdateTransactions(transactions, conn)
      .onComplete(res -> assertTrue(res.succeeded()));

    verify(transactionsDAO).getTransactions(anyList(), any());
    verify(transactionsDAO).updatePermanentTransactions(anyList(), any());

    String budgetTableName = HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE);
    String transactionTableName = HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS);
    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(conn));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(conn));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    BigDecimal amountDifference = newAmount.subtract(amount);

    assertThat(updatedBudget.getAvailable(), is(available.doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(amountDifference).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(encumbered.doubleValue()));

  }

  @Test
  void shouldUpdateBudgetsAndEncumbrancesTotalsWhenUpdateLinkedToEncumbrancePendingPaymentsAmount() {
    BigDecimal newAmount = BigDecimal.valueOf(11d);
    BigDecimal amount = BigDecimal.valueOf(9.99);
    linkedTransaction.withId(UUID.randomUUID().toString())
      .withAmount(newAmount.doubleValue());
    linkedTransaction.getAwaitingPayment().setReleaseEncumbrance(false);

    Transaction existingTransaction = JsonObject.mapFrom(linkedTransaction).mapTo(Transaction.class)
      .withAmount(amount.doubleValue());
    List<Transaction> transactions = Collections.singletonList(linkedTransaction);

    BigDecimal awaitingPayment = BigDecimal.ZERO;
    BigDecimal available = BigDecimal.valueOf(90d);
    BigDecimal encumbered = BigDecimal.valueOf(10d);
    BigDecimal unavailable = BigDecimal.valueOf(10d);

    budget.withAwaitingPayment(awaitingPayment.doubleValue())
      .withAvailable(available.doubleValue())
      .withEncumbered(encumbered.doubleValue())
      .withUnavailable(unavailable.doubleValue());
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(conn)))
      .thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    encumbrance.withAmount(encumbered.doubleValue());
    List<Transaction> encumbrances = Collections.singletonList(encumbrance);

    when(transactionsDAO.getTransactions(eq(Collections.singletonList(linkedTransaction.getAwaitingPayment().getEncumbranceId())), eq(conn)))
      .thenReturn(Future.succeededFuture(encumbrances));
    when(transactionsDAO.getTransactions(eq(Collections.singletonList(linkedTransaction.getId())), any()))
      .thenReturn(Future.succeededFuture(Collections.singletonList(existingTransaction)));
    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    Future<Void> result = spyService.cancelAndUpdateTransactions(transactions, conn);

    assertTrue(result.succeeded());

    final ArgumentCaptor<List<String>> criterionArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO, times(2)).getTransactions(criterionArgumentCaptor.capture(), eq(conn));
    final List<List<String>> idsArguments = criterionArgumentCaptor.getAllValues();
    assertThat(idsArguments, containsInAnyOrder(Collections.singletonList(linkedTransaction.getId()),
      Collections.singletonList(linkedTransaction.getAwaitingPayment().getEncumbranceId())));

    final ArgumentCaptor<List<Transaction>> updateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO, times(2)).updatePermanentTransactions(updateArgumentCapture.capture(), eq(conn));
    List<List<Transaction>> updateArguments = updateArgumentCapture.getAllValues();
    Transaction updatedEncumbrance = updateArguments.stream().flatMap(Collection::stream)
      .filter(transaction -> transaction.getTransactionType() == Transaction.TransactionType.ENCUMBRANCE)
      .findFirst().orElse(null);
    BigDecimal amountDifference = newAmount.subtract(amount);

    assertThat(updatedEncumbrance.getAmount(), is(encumbered.subtract(amountDifference).doubleValue()));
    assertThat(updatedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), is(awaitingPayment.add(amountDifference).doubleValue()));

    Transaction updatedPendingPayment = updateArguments.stream().flatMap(Collection::stream)
      .filter(transaction -> transaction.getTransactionType() == Transaction.TransactionType.PENDING_PAYMENT)
      .findFirst().orElse(null);

    assertThat(updatedPendingPayment.getAmount(), is(newAmount.doubleValue()));

    String budgetTableName = HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE);
    String transactionTableName = HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS);
    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(conn));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(conn));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);



    assertThat(updatedBudget.getAvailable(), is(available.doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(amountDifference).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(encumbered.subtract(amountDifference).doubleValue()));

  }

  @Test
  void shouldUpdateBudgetsWithOverExpendedWhenUpdateNotLinkedToEncumbrancePendingPaymentsAmount() {
    BigDecimal newAmount = BigDecimal.valueOf(1.5);
    BigDecimal amount = BigDecimal.valueOf(20000d);
    notLinkedTransaction.withAmount(newAmount.doubleValue());
    Transaction existingTransaction = JsonObject.mapFrom(notLinkedTransaction).mapTo(Transaction.class)
      .withAmount(amount.doubleValue());
    List<Transaction> transactions = Collections.singletonList(notLinkedTransaction);

    BigDecimal allocated = BigDecimal.valueOf(100d);
    BigDecimal awaitingPayment = BigDecimal.valueOf(20000d);
    BigDecimal available = BigDecimal.ZERO;
    BigDecimal encumbered = BigDecimal.valueOf(10d);
    BigDecimal unavailable = BigDecimal.valueOf(100d);
    BigDecimal overExpended = BigDecimal.valueOf(19910d);
    budget.withAllocated(allocated.doubleValue())
      .withAwaitingPayment(awaitingPayment.doubleValue())
      .withAvailable(available.doubleValue())
      .withEncumbered(encumbered.doubleValue())
      .withUnavailable(unavailable.doubleValue())
      .withOverExpended(overExpended.doubleValue());
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(conn))).thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());
    when(transactionsDAO.getTransactions(anyList(), any()))
      .thenReturn(Future.succeededFuture(Collections.singletonList(existingTransaction)));

    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture());

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    spyService.cancelAndUpdateTransactions(transactions, conn)
      .onComplete(res -> assertTrue(res.succeeded()));

    verify(transactionsDAO).getTransactions(anyList(), any());
    verify(transactionsDAO).updatePermanentTransactions(anyList(), any());

    String budgetTableName = HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE);
    String transactionTableName = HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS);
    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID_FOR_UPDATE, budgetTableName, budgetTableName, transactionTableName);

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(conn));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(conn));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    BigDecimal amountDifference = newAmount.subtract(amount);
    BigDecimal expectedAwaitingPayment = awaitingPayment.add(amountDifference);
    BigDecimal expectedUnavailable = expectedAwaitingPayment.add(encumbered);

    assertThat(updatedBudget.getAvailable(), is(available.doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(expectedAwaitingPayment.doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(encumbered.doubleValue()));

  }

  @Test
  void testCancelTransactions() {
    Transaction newTransaction = JsonObject.mapFrom(notLinkedTransaction).mapTo(Transaction.class)
      .withId(UUID.randomUUID().toString())
      .withAmount(2d)
      .withInvoiceCancelled(true);
    List<Transaction> newTransactions = Collections.singletonList(newTransaction);
    Transaction existingTransaction = JsonObject.mapFrom(newTransaction).mapTo(Transaction.class)
      .withInvoiceCancelled(false);
    List<Transaction> existingTransactions = Collections.singletonList(existingTransaction);

    when(transactionsDAO.getTransactions(anyList(), any())).thenReturn(Future.succeededFuture(existingTransactions));
    when(cancelTransactionService.cancelTransactions(anyList(), any())).thenReturn(Future.succeededFuture(null));

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    spyService.cancelAndUpdateTransactions(newTransactions, conn)
      .onComplete(res -> assertTrue(res.succeeded()));

    verify(transactionsDAO).getTransactions(anyList(), any());
    verify(cancelTransactionService, times(1)).cancelTransactions(anyList(), eq(conn));
  }

  @Test
  void outdatedFundIdInEncumbrance() {
    BigDecimal linkedAmount = BigDecimal.valueOf(2.0);
    linkedTransaction.withAmount(linkedAmount.doubleValue());
    List<Transaction> pendingPayments = Collections.singletonList(linkedTransaction);
    encumbrance.withAmount(1.0);
    encumbrance.getEncumbrance().setSourcePoLineId(UUID.randomUUID().toString());
    List<Transaction> encumbrances = Collections.singletonList(encumbrance);
    List<Budget> budgets = List.of();

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(conn)))
      .thenReturn(Future.succeededFuture(budgets));
    when(transactionsDAO.getTransactions(anyList(), eq(conn)))
      .thenReturn(Future.succeededFuture(encumbrances));

    pendingPaymentService.createTransactions(pendingPayments, conn)
        .onComplete(event -> {
          assertThat(event.cause(), instanceOf(HttpException.class));
          HttpException thrown = (HttpException)event.cause();
          assertThat(thrown.getCode(), is(500));
          Error error = thrown.getErrors().getErrors().get(0);
          assertThat(error.getCode(), is("outdatedFundIdInEncumbrance"));
          List<Parameter> parameters = error.getParameters();
          assertThat(parameters.get(0).getKey(), is("encumbranceId"));
          assertThat(parameters.get(0).getValue(), is(encumbrance.getId()));
          assertThat(parameters.get(1).getKey(), is("fundId"));
          assertThat(parameters.get(1).getValue(), is(fundId));
          assertThat(parameters.get(2).getKey(), is("poLineId"));
          assertThat(parameters.get(2).getValue(), is(encumbrance.getEncumbrance().getSourcePoLineId()));
        }
      );
  }
}
