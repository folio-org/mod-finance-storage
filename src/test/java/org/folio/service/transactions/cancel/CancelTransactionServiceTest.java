package org.folio.service.transactions.cancel;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.budget.BudgetDAO;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.AwaitingPayment;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.folio.service.transactions.PendingPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
  private Transaction transactionFromDB;
  private Budget budget;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
    when(client.getTenantId()).thenReturn(TENANT_ID);
    cancelPendingPaymentService = new CancelPendingPaymentService(transactionsDAO, budgetService);
    cancelPaymentCreditService = new CancelPaymentCreditService(transactionsDAO, budgetService);
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

    transactionFromDB = new Transaction()
      .withId(transaction.getId())
      .withSourceInvoiceId(summaryId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withCurrency(currency)
      .withAmount(10.0)
      .withInvoiceCancelled(false)
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
    BigDecimal amount = BigDecimal.valueOf(9.1);
    transaction.withAmount(amount.doubleValue());

    List<Transaction> transactions = List.of(transaction);

    BigDecimal awaitingPayment = BigDecimal.valueOf(100d);
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

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(client))).thenReturn(Future.succeededFuture());
    when(transactionsDAO.getTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture(List.of(transactionFromDB)));
    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    spyService.createTransactions(transactions, client)
      .onComplete(res -> assertTrue(res.succeeded()));

    transaction.withInvoiceCancelled(true);

    CancelTransactionService cancelTransactionService = Mockito.spy(cancelPendingPaymentService);
    Future<Void> cancelResult = cancelTransactionService.cancelTransactions(transactions, client);
    assertTrue(cancelResult.succeeded());

    final ArgumentCaptor<List<String>> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).getTransactions(listArgumentCaptor.capture(), eq(client));
    final List<String> idsArgument = listArgumentCaptor.getValue();
    assertThat(idsArgument, contains(transaction.getId()));
  }

  @Test
  void cancelPaymentCreditTransaction() {
    BigDecimal amount = BigDecimal.valueOf(9.1);
    transaction.withAmount(amount.doubleValue());

    List<Transaction> transactions = List.of(transaction);

    BigDecimal awaitingPayment = BigDecimal.valueOf(100d);
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

    PendingPaymentService spyService = Mockito.spy(pendingPaymentService);

    when(transactionsDAO.saveTransactionsToPermanentTable(anyString(), eq(client))).thenReturn(Future.succeededFuture());
    when(transactionsDAO.getTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture(List.of(transactionFromDB)));
    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    spyService.createTransactions(transactions, client)
      .onComplete(res -> assertTrue(res.succeeded()));

    transaction.withInvoiceCancelled(true);

    CancelTransactionService cancelTransactionService = Mockito.spy(cancelPaymentCreditService);
    Future<Void> cancelResult = cancelTransactionService.cancelTransactions(transactions, client);
    assertTrue(cancelResult.succeeded());

    final ArgumentCaptor<List<String>> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(transactionsDAO).getTransactions(listArgumentCaptor.capture(), eq(client));
    final List<String> idsArgument = listArgumentCaptor.getValue();
    assertThat(idsArgument, contains(transaction.getId()));
  }
}
