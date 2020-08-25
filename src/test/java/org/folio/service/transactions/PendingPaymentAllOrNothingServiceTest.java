package org.folio.service.transactions;

import static org.folio.dao.transactions.TemporaryInvoiceTransactionDAO.TEMPORARY_INVOICE_TRANSACTIONS;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.service.transactions.PendingPaymentAllOrNothingService.SELECT_BUDGETS_BY_INVOICE_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.money.MonetaryAmount;

import io.vertx.sqlclient.Tuple;
import org.folio.dao.transactions.EncumbranceDAO;
import org.folio.rest.jaxrs.model.AwaitingPayment;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.service.budget.BudgetService;
import org.folio.service.calculation.CalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class PendingPaymentAllOrNothingServiceTest {

  private static final String TENANT_ID = "tenant";

  @InjectMocks
  private PendingPaymentAllOrNothingService pendingPaymentService;

  @Mock
  private CalculationService calculationService;

  @Mock
  private BudgetService budgetService;
  @Mock
  private EncumbranceDAO transactionsDAO;
  @Mock
  private DBClient client;

  private String summaryId = UUID.randomUUID().toString();
  private String encumbranceId = UUID.randomUUID().toString();
  private String fundId = UUID.randomUUID().toString();
  private String fiscalYearId = UUID.randomUUID().toString();
  private String currency = "USD";
  private Transaction linkedTransaction;
  private Transaction notLinkedTransaction;
  private Transaction encumbrance;
  private Budget budget;

  @BeforeEach
  public void initMocks(){
    MockitoAnnotations.initMocks(this);
    when(client.getTenantId()).thenReturn(TENANT_ID);
  }

  @BeforeEach
  public void prepareData(){
    summaryId = UUID.randomUUID().toString();
    encumbranceId = UUID.randomUUID().toString();
    fundId = UUID.randomUUID().toString();
    fiscalYearId = UUID.randomUUID().toString();
    currency = "USD";

    linkedTransaction = new Transaction()
      .withAwaitingPayment(new AwaitingPayment().withEncumbranceId(encumbranceId).withReleaseEncumbrance(false))
      .withSourceInvoiceId(summaryId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withCurrency(currency);

    notLinkedTransaction = new Transaction()
      .withSourceInvoiceId(summaryId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withCurrency(currency);

    encumbrance = new Transaction()
      .withId(encumbranceId)
      .withCurrency(currency)
      .withFromFundId(fundId)
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

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(client))).thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    encumbrance.withAmount(encumbered.doubleValue());
    List<Transaction> encumbrances = Collections.singletonList(encumbrance);

    when(transactionsDAO.getTransactions(any(), eq(client))).thenReturn(Future.succeededFuture(encumbrances));

    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    PendingPaymentAllOrNothingService spyService = Mockito.spy(pendingPaymentService);

    doReturn(Future.succeededFuture()).when(calculationService)
      .updateLedgerFYsWithTotals(anyList(), anyList(), eq(client));

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(client))).thenReturn(Future.succeededFuture());

    Future<Void> result = spyService.processTemporaryToPermanentTransactions(transactions, client);

    assertTrue(result.succeeded());

    final ArgumentCaptor<Criterion> criterionArgumentCaptor = ArgumentCaptor.forClass(Criterion.class);
    verify(transactionsDAO).getTransactions(criterionArgumentCaptor.capture(), eq(client));
    final Criterion criterion = criterionArgumentCaptor.getValue();
    assertThat(criterion.toString(), is(String.format("WHERE   (  id = '%s')    ", encumbranceId)));

    final ArgumentCaptor<List<Transaction>> encumbranceUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).updatePermanentTransactions(encumbranceUpdateArgumentCapture.capture(), eq(client));
    Transaction updatedEncumbrance = encumbranceUpdateArgumentCapture.getValue().get(0);
    assertThat(updatedEncumbrance.getAmount(), is(encumbered.subtract(linkedAmount).doubleValue()));
    assertThat(updatedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), is(BigDecimal.ZERO.add(linkedAmount).doubleValue()));

    verify(transactionsDAO).saveTransactionsToPermanentTable(eq(summaryId), eq(client));

    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID,
      HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE),
      HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS));

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(client));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(client));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    assertThat(updatedBudget.getAvailable(), is(available.subtract(notLinkedAmount).doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.add(notLinkedAmount).doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(linkedAmount).add(notLinkedAmount).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(encumbered.subtract(linkedAmount).doubleValue()));

    verify(calculationService).updateLedgerFYsWithTotals(eq(budgets), eq(updatedBudgets), eq(client));

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

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(client))).thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    encumbrance.withAmount(encumbered.doubleValue());
    encumbrance.getEncumbrance().setAmountAwaitingPayment(awaitingPayment.doubleValue());
    List<Transaction> encumbrances = Collections.singletonList(encumbrance);

    when(transactionsDAO.getTransactions(any(), eq(client))).thenReturn(Future.succeededFuture(encumbrances));

    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    PendingPaymentAllOrNothingService spyService = Mockito.spy(pendingPaymentService);

    doReturn(Future.succeededFuture()).when(calculationService)
      .updateLedgerFYsWithTotals(anyList(), anyList(), eq(client));

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(client))).thenReturn(Future.succeededFuture());

    Future<Void> result = spyService.processTemporaryToPermanentTransactions(transactions, client);

    assertTrue(result.succeeded());

    final ArgumentCaptor<Criterion> criterionArgumentCaptor = ArgumentCaptor.forClass(Criterion.class);
    verify(transactionsDAO).getTransactions(criterionArgumentCaptor.capture(), eq(client));
    final Criterion criterion = criterionArgumentCaptor.getValue();
    assertThat(criterion.toString(), is(String.format("WHERE   (  id = '%s')    ", encumbranceId)));

    final ArgumentCaptor<List<Transaction>> encumbranceUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).updatePermanentTransactions(encumbranceUpdateArgumentCapture.capture(), eq(client));
    Transaction updatedEncumbrance = encumbranceUpdateArgumentCapture.getValue().get(0);
    assertThat(updatedEncumbrance.getAmount(), is(0.0));
    assertThat(updatedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), is(10.1));

    verify(transactionsDAO).saveTransactionsToPermanentTable(eq(summaryId), eq(client));

    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID,
      HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE),
      HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS));

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(client));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(client));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    assertThat(updatedBudget.getAvailable(), is(available.subtract(linkedAmount).add(encumbered).doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.add(linkedAmount).subtract(encumbered).doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(linkedAmount).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(0.0));

    verify(calculationService).updateLedgerFYsWithTotals(eq(budgets), eq(updatedBudgets), eq(client));

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

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(client))).thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    encumbrance.withAmount(encumbered.doubleValue());
    List<Transaction> encumbrances = Collections.singletonList(encumbrance);

    when(transactionsDAO.getTransactions(any(), eq(client))).thenReturn(Future.succeededFuture(encumbrances));

    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    PendingPaymentAllOrNothingService spyService = Mockito.spy(pendingPaymentService);

    doReturn(Future.succeededFuture()).when(calculationService)
      .updateLedgerFYsWithTotals(anyList(), anyList(), eq(client));

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(client))).thenReturn(Future.succeededFuture());

    Future<Void> result = spyService.processTemporaryToPermanentTransactions(transactions, client);

    assertTrue(result.succeeded());

    final ArgumentCaptor<Criterion> criterionArgumentCaptor = ArgumentCaptor.forClass(Criterion.class);
    verify(transactionsDAO).getTransactions(criterionArgumentCaptor.capture(), eq(client));
    final Criterion criterion = criterionArgumentCaptor.getValue();
    assertThat(criterion.toString(), is(String.format("WHERE   (  id = '%s')    ", encumbranceId)));

    final ArgumentCaptor<List<Transaction>> encumbranceUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).updatePermanentTransactions(encumbranceUpdateArgumentCapture.capture(), eq(client));
    Transaction updatedEncumbrance = encumbranceUpdateArgumentCapture.getValue().get(0);
    assertThat(updatedEncumbrance.getAmount(), is(0.0));
    assertThat(updatedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), is(linkedAmount.doubleValue()));

    verify(transactionsDAO).saveTransactionsToPermanentTable(eq(summaryId), eq(client));

    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID,
      HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE),
      HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS));

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(client));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(client));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    assertThat(updatedBudget.getAvailable(), is(available.subtract(linkedAmount).add(encumbered).doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.add(linkedAmount).subtract(encumbered).doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(linkedAmount).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(0d));

    verify(calculationService).updateLedgerFYsWithTotals(eq(budgets), eq(updatedBudgets), eq(client));

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

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(client))).thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    PendingPaymentAllOrNothingService spyService = Mockito.spy(pendingPaymentService);

    doReturn(Future.succeededFuture()).when(calculationService).updateLedgerFYsWithTotals(anyList(), anyList(), eq(client));

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(client))).thenReturn(Future.succeededFuture());

    spyService.processTemporaryToPermanentTransactions(transactions, client)
      .onComplete(res -> assertTrue(res.succeeded()));


    verify(transactionsDAO, never()).getTransactions(any(), any());
    verify(transactionsDAO, never()).updatePermanentTransactions(anyList(), any());

    verify(transactionsDAO).saveTransactionsToPermanentTable(eq(summaryId), eq(client));

    String sql = String.format(SELECT_BUDGETS_BY_INVOICE_ID,
      HelperUtils.getFullTableName(TENANT_ID, BUDGET_TABLE),
      HelperUtils.getFullTableName(TENANT_ID, TEMPORARY_INVOICE_TRANSACTIONS));

    final ArgumentCaptor<Tuple> paramsArgumentCapture = ArgumentCaptor.forClass(Tuple.class);
    verify(budgetService).getBudgets(eq(sql), paramsArgumentCapture.capture(), eq(client));

    Tuple params = paramsArgumentCapture.getValue();
    assertThat(params.get(UUID.class, 0), is(UUID.fromString(summaryId)));

    final ArgumentCaptor<List<Budget>> budgetUpdateArgumentCapture = ArgumentCaptor.forClass(List.class);
    verify(budgetService).updateBatchBudgets(budgetUpdateArgumentCapture.capture(), eq(client));
    List<Budget> updatedBudgets = budgetUpdateArgumentCapture.getValue();
    Budget updatedBudget = updatedBudgets.get(0);

    assertThat(updatedBudget.getAvailable(), is(available.subtract(notLinkedAmount).doubleValue()));
    assertThat(updatedBudget.getUnavailable(), is(unavailable.add(notLinkedAmount).doubleValue()));
    assertThat(updatedBudget.getAwaitingPayment(), is(awaitingPayment.add(notLinkedAmount).doubleValue()));
    assertThat(updatedBudget.getEncumbered(), is(encumbered.doubleValue()));

    verify(calculationService).updateLedgerFYsWithTotals(eq(budgets), eq(updatedBudgets), eq(client));

  }

  @Test
  void TestHandleValidationErrorExceptionThrown() {
    HttpStatusException thrown = assertThrows(
      HttpStatusException.class,
      () -> pendingPaymentService.handleValidationError(new Transaction()),
      "Expected handleValidationError() to throw, but it didn't"
    );

    Parameter parameter = new Parameter().withKey("fromFundId")
      .withValue("null");
    Error error = new Error().withCode("-1")
      .withMessage("may not be null")
      .withParameters(Collections.singletonList(parameter));
    Errors errors = new Errors().withErrors(Collections.singletonList(error)).withTotalRecords(1);
    assertThat(thrown.getStatusCode(), is(422));
    assertThat(thrown.getPayload(), is(JsonObject.mapFrom(errors).encode()));
  }

  @Test
  void testHandleValidationErrorValidTransaction() {
    assertNull(pendingPaymentService.handleValidationError(new Transaction().withFromFundId(fundId)));
  }

  @Test
  void testGetBudgetRemainingAmountForEncumbrance() {
    budget.withExpenditures(90d)
      .withAllowableExpenditure(110d)
      .withAvailable(0d)
      .withUnavailable(100d)
      .withEncumbered(10d);
    MonetaryAmount amount = pendingPaymentService.getBudgetRemainingAmount(budget, currency, new Transaction().withAmount(0d));
    assertThat(amount.getNumber().doubleValue(), is(10d));
  }

  @Test
  void testIsTransactionOverspendRestrictedWithEmptyAllowableExpenditure() {
    assertFalse(pendingPaymentService.isTransactionOverspendRestricted(new Ledger().withRestrictExpenditures(true), budget.withAllowableExpenditure(null)));
  }

  @Test
  void testIsTransactionOverspendRestrictedWithRestrictExpendituresIsFalse() {
    assertFalse(pendingPaymentService.isTransactionOverspendRestricted(new Ledger().withRestrictExpenditures(false), budget.withAllowableExpenditure(110d)));
  }

  @Test
  void testIsTransactionOverspendRestrictedWithRestrictExpendituresIsTrueWithSpecifiedAllowableExpenditure() {
    assertTrue(pendingPaymentService.isTransactionOverspendRestricted(new Ledger().withRestrictExpenditures(true), budget.withAllowableExpenditure(110d)));
  }

}
