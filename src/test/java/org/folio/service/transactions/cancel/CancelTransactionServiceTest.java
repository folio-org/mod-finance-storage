package org.folio.service.transactions.cancel;

import io.vertx.core.Future;
import io.vertx.sqlclient.Tuple;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.AwaitingPayment;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.Transaction.TransactionType;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
  CancelPendingPaymentService cancelPendingPaymentService;
  @InjectMocks
  CancelPaymentCreditService cancelPaymentCreditService;
  @Mock
  TransactionDAO transactionsDAO;
  @Mock
  TransactionDAO encumbranceDAO;
  @Mock
  BudgetService budgetService;
  @Mock
  private DBClient client;

  private static final String TENANT_ID = "tenant";
  private final String summaryId = UUID.randomUUID().toString();
  private final String fundId = UUID.randomUUID().toString();
  private final String fiscalYearId = UUID.randomUUID().toString();
  private final String currency = "USD";
  private Transaction transaction, encumbrance;
  private Budget budget;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
    when(client.getTenantId()).thenReturn(TENANT_ID);
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
      .withInvoiceCancelled(true);
    encumbrance = new Transaction()
      .withId(UUID.randomUUID().toString())
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

  @Test
  void cancelPendingPaymentTransaction() {
    cancelPendingPaymentService = new CancelPendingPaymentService(budgetService, transactionsDAO, encumbranceDAO);

    transaction.setTransactionType(TransactionType.PENDING_PAYMENT);
    transaction.setAwaitingPayment(new AwaitingPayment().withReleaseEncumbrance(true).withEncumbranceId(encumbrance.getId()));
    List<Transaction> transactions = List.of(transaction);
    encumbrance.getEncumbrance().setAmountAwaitingPayment(10.0);
    List<Transaction> encumbrances = List.of(encumbrance);

    budget.withAwaitingPayment(100d)
      .withAvailable(90d)
      .withEncumbered(10d)
      .withUnavailable(10d)
      .withExpenditures(100d);
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(client))).thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture());
    when(encumbranceDAO.getTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture(encumbrances));
    when(encumbranceDAO.updatePermanentTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    Future<Void> cancelResult = cancelPendingPaymentService.cancelTransactions(transactions, client);
    assertTrue(cancelResult.succeeded());

    verify(budgetService, times(1))
      .updateBatchBudgets(argThat(budgetColl -> budgetColl.stream().allMatch(
        b -> b.getAwaitingPayment() == 90d && b.getExpenditures() == 100d
      )), eq(client));
    verify(transactionsDAO, times(1)).updatePermanentTransactions(argThat(trList -> {
      if (trList.size() != 1)
        return false;
      Transaction first = trList.get(0);
      return first.getTransactionType() == TransactionType.PENDING_PAYMENT &&
        first.getInvoiceCancelled() && first.getVoidedAmount() == 10d;
    }), eq(client));
    verify(encumbranceDAO, times(1)).updatePermanentTransactions(argThat(trList -> {
      if (trList.size() != 1)
        return false;
      Transaction first = trList.get(0);
      return first.getTransactionType() == TransactionType.ENCUMBRANCE &&
        first.getEncumbrance().getAmountAwaitingPayment() == 0d;
    }), eq(client));
  }

  @Test
  void cancelPaymentCreditTransaction() {
    cancelPaymentCreditService = new CancelPaymentCreditService(budgetService, transactionsDAO, encumbranceDAO);

    transaction.setTransactionType(TransactionType.PAYMENT);
    transaction.setPaymentEncumbranceId(encumbrance.getId());
    List<Transaction> transactions = List.of(transaction);
    encumbrance.getEncumbrance().setAmountExpended(10.0);
    List<Transaction> encumbrances = List.of(encumbrance);

    budget.withAwaitingPayment(100d)
      .withAvailable(90d)
      .withEncumbered(10d)
      .withUnavailable(10d)
      .withExpenditures(100d);
    List<Budget> budgets = Collections.singletonList(budget);

    when(budgetService.getBudgets(anyString(), any(Tuple.class), eq(client))).thenReturn(Future.succeededFuture(budgets));
    when(budgetService.updateBatchBudgets(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    when(transactionsDAO.updatePermanentTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture());
    when(encumbranceDAO.getTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture(encumbrances));
    when(encumbranceDAO.updatePermanentTransactions(anyList(), eq(client))).thenReturn(Future.succeededFuture());

    Future<Void> cancelResult = cancelPaymentCreditService.cancelTransactions(transactions, client);
    assertTrue(cancelResult.succeeded());

    verify(budgetService, times(1))
      .updateBatchBudgets(argThat(budgetColl -> budgetColl.stream().allMatch(
        b -> b.getAwaitingPayment() == 100d && b.getExpenditures() == 90d
      )), eq(client));
    verify(transactionsDAO, times(1)).updatePermanentTransactions(argThat(trList -> {
      if (trList.size() != 1)
        return false;
      Transaction first = trList.get(0);
      return first.getTransactionType() == TransactionType.PAYMENT &&
        first.getInvoiceCancelled() && first.getVoidedAmount() == 10d;
    }), eq(client));
    verify(encumbranceDAO, times(1)).updatePermanentTransactions(argThat(trList -> {
      if (trList.size() != 1)
        return false;
      Transaction first = trList.get(0);
      return first.getTransactionType() == TransactionType.ENCUMBRANCE &&
        first.getEncumbrance().getAmountExpended() == 0d;
    }), eq(client));
  }
}
