package org.folio.service.transactions.cancel;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CancelPaymentCreditServiceTest {

  private AutoCloseable mockitoMocks;

  @InjectMocks
  private CancelPaymentCreditService cancelPaymentCreditService;

  private final String currency = "USD";


  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  @DisplayName("Credit should be canceled")
  void creditShouldBeCanceled() {
    Transaction transaction = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withAmount(-1000.0)
      .withCurrency(currency)
      .withTransactionType(Transaction.TransactionType.CREDIT);
    List<Transaction> transactionList = List.of(transaction);

    Budget budget = new Budget()
      .withId(UUID.randomUUID().toString())
      .withExpenditures(2000.0);

    Budget resultBudget = cancelPaymentCreditService.budgetMoneyBack(budget, transactionList);

    assertEquals(transaction.getAmount(), 0.0);
    assertEquals(transaction.getVoidedAmount(), -1000.0);
    assertEquals(resultBudget.getExpenditures(), 1000.0);
  }

  @Test
  @DisplayName("Payment should be canceled")
  void paymentShouldBeCanceled() {
    Transaction transaction = new Transaction()
      .withId(UUID.randomUUID().toString())
      .withAmount(1000.0)
      .withCurrency(currency)
      .withTransactionType(Transaction.TransactionType.PAYMENT);
    List<Transaction> transactionList = List.of(transaction);

    Budget budget = new Budget()
      .withId(UUID.randomUUID().toString())
      .withExpenditures(2000.0);

    Budget resultBudget = cancelPaymentCreditService.budgetMoneyBack(budget, transactionList);

    assertEquals(transaction.getAmount(), 0.0);
    assertEquals(transaction.getVoidedAmount(), 1000.0);
    assertEquals(resultBudget.getExpenditures(), 1000.0);
  }
}
