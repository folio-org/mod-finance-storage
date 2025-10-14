package org.folio.service.transactions;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import org.folio.CopilotGenerated;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.AwaitingPayment;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.dao.transactions.BatchTransactionDAO.TRANSACTIONS_TABLE;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.RELEASED;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.UNRELEASED;
import static org.folio.rest.jaxrs.model.Transaction.Source.PO_LINE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.CREDIT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ENCUMBRANCE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PAYMENT;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.service.ServiceTestUtils.createResults;
import static org.folio.service.ServiceTestUtils.createRowSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 0d, 0d, false, false, false);

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
          assertThat(savedBudget.getCredits(), equalTo(0d));

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
  void testCreateCreditWithoutLinkedEncumbrance(VertxTestContext testContext) {
    String creditId = UUID.randomUUID().toString();
    String pendingPaymentId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

    Transaction credit = new Transaction()
      .withId(creditId)
      .withTransactionType(CREDIT)
      .withSourceInvoiceId(invoiceId)
      .withToFundId(fundId)
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
      .withAmount(-5d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withAwaitingPayment(new AwaitingPayment()
        .withReleaseEncumbrance(true));

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(credit);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 5d, 0d, false, false, false);

    Criterion paymentCriterion = createCriterionByIds(List.of(creditId));
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
          Transaction savedCredit = (Transaction)(saveEntities.get(0).get(0));
          assertThat(savedCredit.getTransactionType(), equalTo(CREDIT));
          assertNotNull(savedCredit.getMetadata().getCreatedDate());
          assertThat(savedCredit.getAmount(), equalTo(5d));

          // Verify budget update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(0).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(10d));
          assertThat(savedBudget.getExpenditures(), equalTo(0d));
          assertThat(savedBudget.getCredits(), equalTo(10d));

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

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 10d, 0d, false, false, false);

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
          assertThat(savedEncumbrance.getEncumbrance().getAmountCredited(), equalTo(0d));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(0d));
          assertThat(savedBudget.getExpenditures(), equalTo(5d));
          assertThat(savedBudget.getCredits(), equalTo(10d));

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
  void testCreateCreditWithLinkedEncumbrance(VertxTestContext testContext) {
    String creditId = UUID.randomUUID().toString();
    String pendingPaymentId = UUID.randomUUID().toString();
    String encumbranceId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

    Transaction credit = new Transaction()
      .withId(creditId)
      .withTransactionType(CREDIT)
      .withSourceInvoiceId(invoiceId)
      .withToFundId(fundId)
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
      .withAmount(-5d)
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
    batch.getTransactionsToCreate().add(credit);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 0d, 0d, false, false, false);

    Criterion paymentCriterion = createCriterionByIds(List.of(creditId));
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
          Transaction savedCredit = (Transaction)(saveEntities.get(0).get(0));
          assertThat(savedCredit.getTransactionType(), equalTo(CREDIT));
          assertNotNull(savedCredit.getMetadata().getCreatedDate());
          assertThat(savedCredit.getAmount(), equalTo(5d));

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
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(10d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountExpended(), equalTo(0d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountCredited(), equalTo(5d));


          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(10d));
          assertThat(savedBudget.getExpenditures(), equalTo(0d));
          assertThat(savedBudget.getCredits(), equalTo(5d));

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

  @ParameterizedTest
  @EnumSource(Encumbrance.Status.class)
  void testCancelPaymentWithLinkedEncumbrance(Encumbrance.Status encumbranceStatus, VertxTestContext testContext) {
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
        .withStatus(encumbranceStatus)
        .withAmountAwaitingPayment(0d)
        .withAmountExpended(5d)
        .withInitialAmountEncumbered(5d))
      .withMetadata(new Metadata());

    Batch batch = new Batch();
    batch.getTransactionsToUpdate().add(newPayment);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 10d, 5d, false, false, false);

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
          assertThat(savedEncumbrance.getEncumbrance().getStatus(), equalTo(encumbranceStatus));
          assertNotNull(savedEncumbrance.getMetadata().getUpdatedDate());
          assertThat(savedEncumbrance.getAmount(), equalTo(encumbranceStatus == UNRELEASED ? 5d : 0d));
          assertThat(savedEncumbrance.getEncumbrance().getInitialAmountEncumbered(), equalTo(5d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(0d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountExpended(), equalTo(0d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountCredited(), equalTo(0d));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(encumbranceStatus == UNRELEASED ? 5d : 0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(0d));
          assertThat(savedBudget.getExpenditures(), equalTo(0d));
          assertThat(savedBudget.getCredits(), equalTo(10d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCancelCreditWithUnreleasedEncumbranceAndPositiveAmounts(VertxTestContext testContext) {
    var creditId = UUID.randomUUID().toString();
    var encumbranceId = UUID.randomUUID().toString();
    var invoiceId = UUID.randomUUID().toString();
    var fundId = UUID.randomUUID().toString();
    var fiscalYearId = UUID.randomUUID().toString();
    var currency = "USD";

    var existingCredit = new Transaction()
      .withId(creditId)
      .withTransactionType(CREDIT)
      .withSourceInvoiceId(invoiceId)
      .withToFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(3d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withPaymentEncumbranceId(encumbranceId)
      .withMetadata(new Metadata());

    var cancelledCredit = JsonObject.mapFrom(existingCredit).mapTo(Transaction.class);
    cancelledCredit.setInvoiceCancelled(true);

    // Create an UNRELEASED encumbrance with positive amounts to trigger lines 85-90
    var existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency(currency)
      .withFromFundId(fundId)
      .withAmount(10d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(UNRELEASED)
        .withAmountAwaitingPayment(2d)  // Positive amount
        .withAmountExpended(1d)         // Positive amount
        .withAmountCredited(5d)         // Positive amount
        .withInitialAmountEncumbered(15d))
      .withMetadata(new Metadata());

    var batch = new Batch();
    batch.getTransactionsToUpdate().add(cancelledCredit);

    setupFundBudgetLedger(fundId, fiscalYearId, 10d, 2d, 5d, 1d, false, false, false);

    var creditCriterion = createCriterionByIds(List.of(creditId));
    doReturn(succeededFuture(createResults(List.of(existingCredit))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(creditCriterion.toString())));

    var encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
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
          // Verify credit and encumbrance updates
          var updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          var updateTableNames = updateTableNamesCaptor.getAllValues();
          var updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          var savedCredit = (Transaction)(updateEntities.get(0).get(0));
          assertThat(savedCredit.getTransactionType(), equalTo(CREDIT));
          assertNotNull(savedCredit.getMetadata().getUpdatedDate());
          assertThat(savedCredit.getAmount(), equalTo(0d));
          assertThat(savedCredit.getVoidedAmount(), equalTo(3d));

          // Verify encumbrance update - this tests lines 85-90 specifically
          var savedEncumbrance = (Transaction)(updateEntities.get(0).get(1));
          assertThat(savedEncumbrance.getTransactionType(), equalTo(ENCUMBRANCE));
          assertThat(savedEncumbrance.getEncumbrance().getStatus(), equalTo(UNRELEASED));
          assertNotNull(savedEncumbrance.getMetadata().getUpdatedDate());

          // Lines 85-90: credited amount should be reduced and encumbrance amount should be reduced
          assertThat(savedEncumbrance.getEncumbrance().getAmountCredited(), equalTo(2d)); // 5d - 3d
          assertThat(savedEncumbrance.getAmount(), equalTo(7d)); // 10d - 3d (lines 87-89)
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(2d)); // unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountExpended(), equalTo(1d)); // unchanged

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          var savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(7d)); // Updated based on new encumbrance amount
          assertThat(savedBudget.getAwaitingPayment(), equalTo(2d));
          assertThat(savedBudget.getExpenditures(), equalTo(1d));
          assertThat(savedBudget.getCredits(), equalTo(2d)); // 5d - 3d
        });
        testContext.completeNow();
      });
  }


  @Test
  void testCancelCreditWithUnreleasedEncumbranceAndOnlyAwaitingPayment(VertxTestContext testContext) {
    var creditId = UUID.randomUUID().toString();
    var encumbranceId = UUID.randomUUID().toString();
    var invoiceId = UUID.randomUUID().toString();
    var fundId = UUID.randomUUID().toString();
    var fiscalYearId = UUID.randomUUID().toString();
    var currency = "USD";

    var existingCredit = new Transaction()
      .withId(creditId)
      .withTransactionType(CREDIT)
      .withSourceInvoiceId(invoiceId)
      .withToFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(2d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withPaymentEncumbranceId(encumbranceId)
      .withMetadata(new Metadata());

    var cancelledCredit = JsonObject.mapFrom(existingCredit).mapTo(Transaction.class);
    cancelledCredit.setInvoiceCancelled(true);

    // Create UNRELEASED encumbrance with only awaitingPayment > 0 (expended = 0, credited = 0)
    var existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency(currency)
      .withFromFundId(fundId)
      .withAmount(8d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(UNRELEASED)
        .withAmountAwaitingPayment(3d)  // Only this is positive
        .withAmountExpended(0d)         // Zero
        .withAmountCredited(0d)         // Zero
        .withInitialAmountEncumbered(10d))
      .withMetadata(new Metadata());

    var batch = new Batch();
    batch.getTransactionsToUpdate().add(cancelledCredit);

    setupFundBudgetLedger(fundId, fiscalYearId, 8d, 3d, 0d, 0d, false, false, false);

    var creditCriterion = createCriterionByIds(List.of(creditId));
    doReturn(succeededFuture(createResults(List.of(existingCredit))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(creditCriterion.toString())));

    var encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
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
          var updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          var updateEntities = updateEntitiesCaptor.getAllValues();

          var savedEncumbrance = (Transaction)(updateEntities.get(0).get(1));
          // Verify lines 85-90 executed with awaitingPayment > 0 condition
          assertThat(savedEncumbrance.getAmount(), equalTo(6d)); // 8d - 2d
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(3d)); // unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountExpended(), equalTo(0d)); // unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountCredited(), equalTo(0d)); // 0d - 2d = 0d (default)
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCancelCreditWithUnreleasedEncumbranceAndOnlyExpended(VertxTestContext testContext) {
    var creditId = UUID.randomUUID().toString();
    var encumbranceId = UUID.randomUUID().toString();
    var invoiceId = UUID.randomUUID().toString();
    var fundId = UUID.randomUUID().toString();
    var fiscalYearId = UUID.randomUUID().toString();
    var currency = "USD";

    var existingCredit = new Transaction()
      .withId(creditId)
      .withTransactionType(CREDIT)
      .withSourceInvoiceId(invoiceId)
      .withToFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(1.5d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withPaymentEncumbranceId(encumbranceId)
      .withMetadata(new Metadata());

    var cancelledCredit = JsonObject.mapFrom(existingCredit).mapTo(Transaction.class);
    cancelledCredit.setInvoiceCancelled(true);

    // Create UNRELEASED encumbrance with only expended > 0 (awaitingPayment = 0, credited = 0)
    var existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency(currency)
      .withFromFundId(fundId)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(UNRELEASED)
        .withAmountAwaitingPayment(0d)  // Zero
        .withAmountExpended(2d)         // Only this is positive
        .withAmountCredited(0d)         // Zero
        .withInitialAmountEncumbered(8d))
      .withMetadata(new Metadata());

    var batch = new Batch();
    batch.getTransactionsToUpdate().add(cancelledCredit);

    setupFundBudgetLedger(fundId, fiscalYearId, 5d, 0d, 0d, 2d, false, false, false);

    var creditCriterion = createCriterionByIds(List.of(creditId));
    doReturn(succeededFuture(createResults(List.of(existingCredit))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(creditCriterion.toString())));

    var encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
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
          var updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          var updateEntities = updateEntitiesCaptor.getAllValues();

          var savedEncumbrance = (Transaction)(updateEntities.get(0).get(1));
          // Verify lines 85-90 executed with expended > 0 condition
          assertThat(savedEncumbrance.getAmount(), equalTo(3.5d)); // 5d - 1.5d
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(0d)); // unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountExpended(), equalTo(2d)); // unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountCredited(), equalTo(0d)); // 0d - 1.5d = 0d (default)
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCancelCreditWithUnreleasedEncumbranceAndOnlyCredited(VertxTestContext testContext) {
    var creditId = UUID.randomUUID().toString();
    var encumbranceId = UUID.randomUUID().toString();
    var invoiceId = UUID.randomUUID().toString();
    var fundId = UUID.randomUUID().toString();
    var fiscalYearId = UUID.randomUUID().toString();
    var currency = "USD";

    var existingCredit = new Transaction()
      .withId(creditId)
      .withTransactionType(CREDIT)
      .withSourceInvoiceId(invoiceId)
      .withToFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(1d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withPaymentEncumbranceId(encumbranceId)
      .withMetadata(new Metadata());

    var cancelledCredit = JsonObject.mapFrom(existingCredit).mapTo(Transaction.class);
    cancelledCredit.setInvoiceCancelled(true);

    // Create UNRELEASED encumbrance with only credited > 0 (awaitingPayment = 0, expended = 0)
    var existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency(currency)
      .withFromFundId(fundId)
      .withAmount(6d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(UNRELEASED)
        .withAmountAwaitingPayment(0d)  // Zero
        .withAmountExpended(0d)         // Zero
        .withAmountCredited(3d)         // Only this is positive
        .withInitialAmountEncumbered(9d))
      .withMetadata(new Metadata());

    var batch = new Batch();
    batch.getTransactionsToUpdate().add(cancelledCredit);

    setupFundBudgetLedger(fundId, fiscalYearId, 6d, 0d, 3d, 0d, false, false, false);

    var creditCriterion = createCriterionByIds(List.of(creditId));
    doReturn(succeededFuture(createResults(List.of(existingCredit))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(creditCriterion.toString())));

    var encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
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
          var updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          var updateEntities = updateEntitiesCaptor.getAllValues();

          var savedEncumbrance = (Transaction)(updateEntities.get(0).get(1));
          // Verify lines 85-90 executed with credited > 0 condition
          assertThat(savedEncumbrance.getAmount(), equalTo(5d)); // 6d - 1d
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(0d)); // unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountExpended(), equalTo(0d)); // unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountCredited(), equalTo(2d)); // 3d - 1d
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCancelCreditWithUnreleasedEncumbranceAndAllZeroAmounts(VertxTestContext testContext) {
    var creditId = UUID.randomUUID().toString();
    var encumbranceId = UUID.randomUUID().toString();
    var invoiceId = UUID.randomUUID().toString();
    var fundId = UUID.randomUUID().toString();
    var fiscalYearId = UUID.randomUUID().toString();
    var currency = "USD";

    var existingCredit = new Transaction()
      .withId(creditId)
      .withTransactionType(CREDIT)
      .withSourceInvoiceId(invoiceId)
      .withToFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(2d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withPaymentEncumbranceId(encumbranceId)
      .withMetadata(new Metadata());

    var cancelledCredit = JsonObject.mapFrom(existingCredit).mapTo(Transaction.class);
    cancelledCredit.setInvoiceCancelled(true);

    // Create UNRELEASED encumbrance with all amounts = 0 (should NOT trigger lines 87-89)
    var existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency(currency)
      .withFromFundId(fundId)
      .withAmount(4d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(UNRELEASED)
        .withAmountAwaitingPayment(0d)  // Zero
        .withAmountExpended(0d)         // Zero
        .withAmountCredited(0d)         // Zero
        .withInitialAmountEncumbered(6d))
      .withMetadata(new Metadata());

    var batch = new Batch();
    batch.getTransactionsToUpdate().add(cancelledCredit);

    setupFundBudgetLedger(fundId, fiscalYearId, 4d, 0d, 0d, 0d, false, false, false);

    var creditCriterion = createCriterionByIds(List.of(creditId));
    doReturn(succeededFuture(createResults(List.of(existingCredit))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(creditCriterion.toString())));

    var encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
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
          var updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          var updateEntities = updateEntitiesCaptor.getAllValues();

          var savedEncumbrance = (Transaction)(updateEntities.get(0).get(1));
          // Verify lines 87-89 NOT executed (all amounts are 0, so condition is false)
          assertThat(savedEncumbrance.getAmount(), equalTo(4d)); // Should remain unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(0d)); // unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountExpended(), equalTo(0d)); // unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountCredited(), equalTo(0d)); // 0d - 2d = 0d (default)
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCancelCreditWithReleasedEncumbrance(VertxTestContext testContext) {
    var creditId = UUID.randomUUID().toString();
    var encumbranceId = UUID.randomUUID().toString();
    var invoiceId = UUID.randomUUID().toString();
    var fundId = UUID.randomUUID().toString();
    var fiscalYearId = UUID.randomUUID().toString();
    var currency = "USD";

    var existingCredit = new Transaction()
      .withId(creditId)
      .withTransactionType(CREDIT)
      .withSourceInvoiceId(invoiceId)
      .withToFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(2d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withPaymentEncumbranceId(encumbranceId)
      .withMetadata(new Metadata());

    var cancelledCredit = JsonObject.mapFrom(existingCredit).mapTo(Transaction.class);
    cancelledCredit.setInvoiceCancelled(true);

    // Create RELEASED encumbrance (should NOT trigger lines 87-89 regardless of amounts)
    var existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency(currency)
      .withFromFundId(fundId)
      .withAmount(3d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(RELEASED)  // RELEASED, not UNRELEASED
        .withAmountAwaitingPayment(1d)  // Positive
        .withAmountExpended(1d)         // Positive
        .withAmountCredited(3d)         // Positive
        .withInitialAmountEncumbered(8d))
      .withMetadata(new Metadata());

    var batch = new Batch();
    batch.getTransactionsToUpdate().add(cancelledCredit);

    setupFundBudgetLedger(fundId, fiscalYearId, 3d, 1d, 3d, 1d, false, false, false);

    var creditCriterion = createCriterionByIds(List.of(creditId));
    doReturn(succeededFuture(createResults(List.of(existingCredit))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(creditCriterion.toString())));

    var encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
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
          var updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          var updateEntities = updateEntitiesCaptor.getAllValues();

          var savedEncumbrance = (Transaction)(updateEntities.get(0).get(1));
          // Verify lines 87-89 NOT executed (encumbrance is RELEASED)
          assertThat(savedEncumbrance.getAmount(), equalTo(3d)); // Should remain unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(1d)); // unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountExpended(), equalTo(1d)); // unchanged
          assertThat(savedEncumbrance.getEncumbrance().getAmountCredited(), equalTo(1d)); // 3d - 2d
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
          HttpException exception = (HttpException)(event.cause());
          assertEquals("paymentOrCreditHasNegativeAmount", exception.getErrors().getErrors().get(0).getCode());
          assertThat(exception.getCode(), equalTo(422));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreatePaymentWithBadEncumbranceLink(VertxTestContext testContext) {
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
        .withReleaseEncumbrance(true));

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(payment);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 0d, 0d, false, false, false);

    Criterion paymentCriterion = createCriterionByIds(List.of(paymentId));
    doReturn(succeededFuture(createResults(List.of())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(paymentCriterion.toString())));

    String snippet = "WHERE (jsonb->>'transactionType') = 'Pending payment'  AND  (  (jsonb->>'sourceInvoiceId') = '" + invoiceId + "')    ";
    doReturn(succeededFuture(createResults(List.of(existingPendingPayment))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(snippet)));

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

  @Test
  @CopilotGenerated(partiallyGenerated = true, model = "GPT-4.1")
  void testPaymentWithEncumbranceCreditsAddedBackWhenCappedAtInitialAmount(VertxTestContext testContext) {
    var paymentId = UUID.randomUUID().toString();
    var pendingPaymentId = UUID.randomUUID().toString();
    var encumbranceId = UUID.randomUUID().toString();
    var invoiceId = UUID.randomUUID().toString();
    var fundId = UUID.randomUUID().toString();
    var fiscalYearId = UUID.randomUUID().toString();
    var currency = "USD";

    var payment = new Transaction()
      .withId(paymentId)
      .withTransactionType(PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(3d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withPaymentEncumbranceId(encumbranceId);

    var existingPendingPayment = new Transaction()
      .withId(pendingPaymentId)
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(3d)
      .withCurrency(currency)
      .withInvoiceCancelled(false)
      .withAwaitingPayment(new AwaitingPayment()
        .withEncumbranceId(encumbranceId)
        .withReleaseEncumbrance(true));

    // Create encumbrance scenario where amount + awaitingPayment + expended < initialAmountEncumbered && credited > 0
    // This triggers lines 119-123: adding credited amount back to encumbrance
    var existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withTransactionType(ENCUMBRANCE)
      .withCurrency(currency)
      .withFromFundId(fundId)
      .withAmount(8d)  // Current encumbrance amount (capped)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(RELEASED)
        .withAmountAwaitingPayment(3d)
        .withAmountExpended(2d)
        .withAmountCredited(4d)  // Existing credits > 0 (triggers condition)
        .withInitialAmountEncumbered(15d)) // Initial amount > (3+0+5) = 8
      .withMetadata(new Metadata());

    var batch = new Batch();
    batch.getTransactionsToCreate().add(payment);

    setupFundBudgetLedger(fundId, fiscalYearId, 8d, 3d, 10d, 2d, false, false, false);

    var paymentCriterion = createCriterionByIds(List.of(paymentId));
    doReturn(succeededFuture(createResults(List.of())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(paymentCriterion.toString())));

    var encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(encumbranceCriterion.toString())));

    var snippet = "WHERE (jsonb->>'transactionType') = 'Pending payment'  AND  (  (jsonb->>'sourceInvoiceId') = '" + invoiceId + "')    ";
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
          var saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          var saveTableNames = saveTableNamesCaptor.getAllValues();
          var saveEntities = saveEntitiesCaptor.getAllValues();
          assertThat(saveTableNames.getFirst(), equalTo(TRANSACTIONS_TABLE));

          var savedPayment = (Transaction)(saveEntities.getFirst().getFirst());
          assertThat(savedPayment.getTransactionType(), equalTo(PAYMENT));
          assertNotNull(savedPayment.getMetadata().getCreatedDate());
          assertThat(savedPayment.getAmount(), equalTo(3d));

          // Verify encumbrance update - this tests lines 119-123 specifically
          var updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          var updateTableNames = updateTableNamesCaptor.getAllValues();
          var updateEntities = updateEntitiesCaptor.getAllValues();
          assertThat(updateTableNames.getFirst(), equalTo(TRANSACTIONS_TABLE));

          var savedEncumbrance = (Transaction)(updateEntities.getFirst().getFirst());
          assertThat(savedEncumbrance.getTransactionType(), equalTo(ENCUMBRANCE));
          assertNotNull(savedEncumbrance.getMetadata().getUpdatedDate());

          // Verify lines 119-123: Since (3 + 0 + 5) = 8 < 15 && credited (4) > 0
          // The encumbrance amount should be increased by credited amount: 8 + 4 = 12
          assertThat(savedEncumbrance.getAmount(), equalTo(12d)); // 8d + 4d (credited added back)
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(0d)); // 3d - 3d
          assertThat(savedEncumbrance.getEncumbrance().getAmountExpended(), equalTo(5d)); // 2d + 3d
          assertThat(savedEncumbrance.getEncumbrance().getAmountCredited(), equalTo(4d)); // unchanged

          // Verify pending payment deletion
          var deleteTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          var deleteCriterionCaptor = ArgumentCaptor.forClass(Criterion.class);
          verify(conn, times(1)).delete(deleteTableNamesCaptor.capture(), deleteCriterionCaptor.capture());
          var deleteTableNames = deleteTableNamesCaptor.getAllValues();
          var deleteCriteria = deleteCriterionCaptor.getAllValues();

          var pendingPaymentCriterionByIds = createCriterionByIds(List.of(pendingPaymentId));
          assertThat(deleteTableNames.getFirst(), equalTo(TRANSACTIONS_TABLE));
          var deleteCriterion = deleteCriteria.getFirst();
          assertThat(deleteCriterion.toString(), equalTo(pendingPaymentCriterionByIds.toString()));
        });
        testContext.completeNow();
      });
  }

}
