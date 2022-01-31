package org.folio.service.transactions.cancel;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.folio.service.transactions.PendingPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CancelTransactionServiceTest {

  @InjectMocks
  private PendingPaymentService pendingPaymentService;

  CancelTransactionService cancelPaymentCreditService;
  CancelTransactionService cancelPendingPaymentService;

  @Mock
  TransactionDAO transactionsDAO;
  @Mock
  BudgetService budgetService;
  @Mock
  private DBClient client;

  private static final String TENANT_ID = "tenant";
  private final String summaryId = UUID.randomUUID().toString();
  private final String fundId = UUID.randomUUID().toString();
  private final String fiscalYearId = UUID.randomUUID().toString();
  private final String currency = "USD";
  private Transaction transaction;
  private Budget budget;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
    when(client.getTenantId()).thenReturn(TENANT_ID);
    cancelPendingPaymentService = new CancelPendingPaymentService(budgetService);
    cancelPaymentCreditService = new CancelPaymentCreditService(budgetService);
  }

  @BeforeEach
  public void prepareData(){
    transaction = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withSourceInvoiceId(summaryId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withCurrency(currency)
      .withAmount(10.0)
      .withTransactionType(Transaction.TransactionType.PENDING_PAYMENT);

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
  void cancelPendingPaymentTransaction() {
    List<Transaction> transactions = List.of(transaction);

    budget.withAwaitingPayment(100d)
      .withAvailable(90d)
      .withEncumbered(10d)
      .withUnavailable(10d)
      .withExpenditures(100d);
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(client))).thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(client))).thenReturn(Future.succeededFuture());

    spyService.createTransactions(transactions, client)
      .onComplete(res -> assertTrue(res.succeeded()));

    transaction.withInvoiceCancelled(true);

    CancelTransactionService cancelTransactionService = Mockito.spy(cancelPendingPaymentService);
    Future<List<Transaction>> cancelResult = cancelTransactionService.cancelTransactions(transactions, client);
    assertTrue(cancelResult.succeeded());
    List<Transaction> result = cancelResult.result();
    assertEquals(1, result.size());
    Transaction firstResult = result.get(0);
    assertEquals(true, firstResult.getInvoiceCancelled());
    assertEquals(10d, firstResult.getVoidedAmount());

    verify(budgetService, times(1))
      .updateBatchBudgets(argThat(budgetColl -> budgetColl.stream().allMatch(
        b -> b.getAwaitingPayment() == 90d && b.getExpenditures() == 100d
      )), eq(client));
  }

  @Test
  void cancelPaymentCreditTransaction() {
    List<Transaction> transactions = List.of(transaction);

    budget.withAwaitingPayment(100d)
      .withAvailable(90d)
      .withEncumbered(10d)
      .withUnavailable(10d)
      .withExpenditures(100d);
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(client))).thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(client))).thenReturn(Future.succeededFuture());

    spyService.createTransactions(transactions, client)
      .onComplete(res -> assertTrue(res.succeeded()));

    transaction.withInvoiceCancelled(true);

    CancelTransactionService cancelTransactionService = Mockito.spy(cancelPaymentCreditService);
    Future<List<Transaction>> cancelResult = cancelTransactionService.cancelTransactions(transactions, client);
    assertTrue(cancelResult.succeeded());
    List<Transaction> result = cancelResult.result();
    assertEquals(1, result.size());
    Transaction firstResult = result.get(0);
    assertEquals(true, firstResult.getInvoiceCancelled());
    assertEquals(10d, firstResult.getVoidedAmount());

    verify(budgetService, times(1))
      .updateBatchBudgets(argThat(budgetColl -> budgetColl.stream().allMatch(
        b -> b.getAwaitingPayment() == 100d && b.getExpenditures() == 90d
      )), eq(client));
  }
}
