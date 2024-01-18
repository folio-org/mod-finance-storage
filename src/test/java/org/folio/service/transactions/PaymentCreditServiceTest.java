package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBConn;
import org.folio.service.transactions.cancel.CancelTransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PaymentCreditServiceTest {

  private AutoCloseable mockitoMocks;
  @InjectMocks
  private PaymentCreditService paymentCreditService;
  @Mock
  private CancelTransactionService cancelTransactionService;
  @Mock
  private TransactionDAO transactionsDAO;
  @Mock
  private DBConn conn;

  @BeforeEach
  public void initMocks(){
    mockitoMocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void testCancelTransactions() {
    Transaction newPayment = new Transaction()
      .withId(randomUUID().toString())
      .withSourceInvoiceId(randomUUID().toString())
      .withFromFundId(randomUUID().toString())
      .withFiscalYearId(randomUUID().toString())
      .withCurrency("USD")
      .withTransactionType(Transaction.TransactionType.PAYMENT)
      .withAmount(10d)
      .withInvoiceCancelled(true);
    Transaction newCredit = new Transaction()
      .withId(randomUUID().toString())
      .withSourceInvoiceId(randomUUID().toString())
      .withFromFundId(randomUUID().toString())
      .withFiscalYearId(randomUUID().toString())
      .withCurrency("USD")
      .withTransactionType(Transaction.TransactionType.CREDIT)
      .withAmount(5d)
      .withInvoiceCancelled(true);
    Transaction newPaymentToIgnore = JsonObject.mapFrom(newPayment).mapTo(Transaction.class)
      .withId(randomUUID().toString());
    List<Transaction> newTransactions = List.of(newPayment, newCredit, newPaymentToIgnore);
    Transaction existingPayment = JsonObject.mapFrom(newPayment).mapTo(Transaction.class)
      .withInvoiceCancelled(false);
    Transaction existingCredit = JsonObject.mapFrom(newCredit).mapTo(Transaction.class)
      .withInvoiceCancelled(false);
    List<Transaction> existingTransactions = List.of(existingPayment, existingCredit, newPaymentToIgnore);

    when(transactionsDAO.getTransactions(anyList(), any()))
      .thenReturn(Future.succeededFuture(existingTransactions));
    when(cancelTransactionService.cancelTransactions(anyList(), any()))
      .then(args -> Future.succeededFuture(args.getArgument(0)));

    PaymentCreditService spyService = Mockito.spy(paymentCreditService);

    spyService.updateTransactions(newTransactions, conn)
      .onComplete(res -> assertTrue(res.succeeded()));

    verify(transactionsDAO, times(1)).getTransactions(anyList(), eq(conn));
    verify(cancelTransactionService, times(1)).cancelTransactions(argThat(list -> list.size() == 2), eq(conn));
  }

}
