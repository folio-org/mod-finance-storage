package org.folio.service.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.folio.dao.summary.InvoiceTransactionSummaryDAO;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.InvoiceTransactionSummary;
import org.folio.rest.jaxrs.model.Transaction;
import org.junit.jupiter.api.Test;

public class PendingPaymentTransactionSummaryServiceTest {

  private final PendingPaymentTransactionSummaryService pendingPaymentSummaryService = new PendingPaymentTransactionSummaryService(new InvoiceTransactionSummaryDAO());

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
    assertEquals(2, summary.getNumPaymentsCredits());
    assertEquals(-2, summary.getNumPendingPayments());
  }

  @Test
  void getNumTransactions() {
    InvoiceTransactionSummary summary = new InvoiceTransactionSummary()
      .withNumPendingPayments(-2)
      .withNumPaymentsCredits(2);
    assertEquals(-2, pendingPaymentSummaryService.getNumTransactions(summary));
  }
}
