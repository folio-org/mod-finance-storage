package org.folio.service.transactions;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.AwaitingPayment;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.dao.transactions.BatchTransactionDAO.TRANSACTIONS_TABLE;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.RELEASED;
import static org.folio.rest.jaxrs.model.Transaction.Source.PO_LINE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ENCUMBRANCE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
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

public class PaymentCreditTest extends BatchTransactionServiceTestBase {

  @Test
  void testCreatePaymentWithoutLinkedEncumbrance(VertxTestContext testContext) {
    String paymentId = UUID.randomUUID().toString();
    String pendingPaymentId = UUID.randomUUID().toString();
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
      .withInvoiceCancelled(false);

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
        .withReleaseEncumbrance(true));

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(payment);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 0d, false, false, false);

    Criterion paymentCriterion = createCriterionByIds(List.of(paymentId));
    doReturn(succeededFuture(createResults(List.of())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(paymentCriterion.toString())));

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
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();

          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedPayment = (Transaction)(saveEntities.get(0).get(0));
          assertThat(savedPayment.getTransactionType(), equalTo(PAYMENT));
          assertNotNull(savedPayment.getMetadata().getCreatedDate());
          assertThat(savedPayment.getAmount(), equalTo(5d));

          // Verify budget update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(0).get(0));
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

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 0d, false, false, false);

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

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 5d, false, false, false);

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

  @Test
  void testCreatePaymentWithBadEncumbranceLink(VertxTestContext testContext) {
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
      .withAmount(5d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withPaymentEncumbranceId(encumbranceId);

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(payment);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 0d, false, false, false);

    Criterion paymentCriterion = createCriterionByIds(List.of(paymentId));
    doReturn(succeededFuture(createResults(List.of())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(paymentCriterion.toString())));

    Criterion encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(Collections.emptyList())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(encumbranceCriterion.toString())));

    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertThat(event.cause(), instanceOf(HttpException.class));
          assertThat(((HttpException) event.cause()).getCode(), equalTo(400));
        });
        testContext.completeNow();
      });
  }

}
