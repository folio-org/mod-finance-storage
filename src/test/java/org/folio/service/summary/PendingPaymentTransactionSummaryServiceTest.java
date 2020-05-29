package org.folio.service.summary;

import org.folio.dao.summary.InvoiceTransactionSummaryDAO;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PendingPaymentTransactionSummaryServiceTest {

  private PendingPaymentTransactionSummaryService pendingPaymentSummaryService = new PendingPaymentTransactionSummaryService(new InvoiceTransactionSummaryDAO());

  @Test
  void testGetSummaryId() {
    String invoiceId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction transaction = new Transaction()
      .withSourceInvoiceId(invoiceId)
      .withEncumbrance(new Encumbrance().withSourcePurchaseOrderId(orderId));

    assertEquals(pendingPaymentSummaryService.getSummaryId(transaction), invoiceId);
  }

  @Test
  void testIsProcessedTrue() {
    InvoiceTransactionSummary summary = new InvoiceTransactionSummary()
      .withNumPendingPayments(-2)
      .withNumPaymentsCredits(2);
    assertTrue(pendingPaymentSummaryService.isProcessed(summary));
  }

  @Test
  void testIsProcessedFalse() {
    InvoiceTransactionSummary summary = new InvoiceTransactionSummary()
      .withNumPendingPayments(2)
      .withNumPaymentsCredits(-2);
    assertFalse(pendingPaymentSummaryService.isProcessed(summary));
  }

  @Test
  void setTransactionsSummariesProcessed() {
    InvoiceTransactionSummary summary = new InvoiceTransactionSummary()
      .withNumPendingPayments(2)
      .withNumPaymentsCredits(2);
    pendingPaymentSummaryService.setTransactionsSummariesProcessed(summary);
    assertEquals(summary.getNumPaymentsCredits(), 2);
    assertEquals(summary.getNumPendingPayments(), -2);
  }

  @Test
  void getNumTransactions() {
    InvoiceTransactionSummary summary = new InvoiceTransactionSummary()
      .withNumPendingPayments(-2)
      .withNumPaymentsCredits(2);
    assertEquals(pendingPaymentSummaryService.getNumTransactions(summary), -2);
  }
}
