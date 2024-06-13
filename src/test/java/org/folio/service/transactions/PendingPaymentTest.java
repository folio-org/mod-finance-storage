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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.dao.transactions.BatchTransactionDAO.TRANSACTIONS_TABLE;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.RELEASED;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.UNRELEASED;
import static org.folio.rest.jaxrs.model.Transaction.Source.PO_LINE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ENCUMBRANCE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.PENDING_PAYMENT;
import static org.folio.rest.util.ErrorCodes.BUDGET_RESTRICTED_EXPENDITURES_ERROR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PendingPaymentTest extends BatchTransactionServiceTestBase {

  @Test
  void testBatchEntityValidity(VertxTestContext testContext) {
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";
    Transaction pendingPayment = new Transaction()
      .withTransactionType(PENDING_PAYMENT)
      .withAmount(0d)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withCurrency(currency);
    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(pendingPayment);
    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onFailure(thrown -> {
        testContext.verify(() -> {
          assertThat(thrown, instanceOf(HttpException.class));
          HttpException exception = (HttpException)thrown;
          assertThat(exception.getCode(), equalTo(400));
          assertEquals(exception.getErrors().getErrors().get(0).getCode(), "idIsRequiredInTransactions");
          assertThat(exception.getMessage(), equalTo("Id is required in transactions to create."));
        });
        testContext.completeNow();
      });
  }

  @ParameterizedTest
  @ValueSource(doubles = {5d, 15d})
  void testCreatePendingPaymentWithLinkedEncumbrance(double pendingPaymentAmount, VertxTestContext testContext) {
    // In the second test, the pending payment amount is greater than available budget, and also greater than the encumbrance
    // Expenditure restrictions are disabled
    String pendingPaymentId = UUID.randomUUID().toString();
    String encumbranceId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

    Transaction pendingPayment = new Transaction()
      .withId(pendingPaymentId)
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(pendingPaymentAmount)
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
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(UNRELEASED)
        .withAmountAwaitingPayment(0d)
        .withInitialAmountEncumbered(5d))
      .withMetadata(new Metadata());

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(pendingPayment);

    setupFundBudgetLedger(fundId, fiscalYearId, 5d, 0d, 0d, 0d, false, false, false);

    Criterion pendingPaymentCriterion = createCriterionByIds(List.of(pendingPaymentId));
    doReturn(succeededFuture(createResults(List.of())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(pendingPaymentCriterion.toString())));

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
          // Verify pending payment creation
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();

          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedPendingPayment = (Transaction)(saveEntities.get(0).get(0));
          assertThat(savedPendingPayment.getTransactionType(), equalTo(PENDING_PAYMENT));
          assertNotNull(savedPendingPayment.getMetadata().getUpdatedDate());
          assertThat(savedPendingPayment.getAmount(), equalTo(pendingPaymentAmount));

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
          assertThat(savedEncumbrance.getEncumbrance().getStatus(), equalTo(RELEASED));
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(pendingPaymentAmount));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(pendingPaymentAmount));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testUpdatePendingPaymentWithLinkedEncumbrance(VertxTestContext testContext) {
    String pendingPaymentId = UUID.randomUUID().toString();
    String encumbranceId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

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
        .withReleaseEncumbrance(true))
      .withMetadata(new Metadata());

    Transaction newPendingPayment = JsonObject.mapFrom(existingPendingPayment).mapTo(Transaction.class);
    newPendingPayment.setAmount(10d);

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
    batch.getTransactionsToUpdate().add(newPendingPayment);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 0d, 0d, false, false, false);

    Criterion pendingPaymentCriterion = createCriterionByIds(List.of(pendingPaymentId));
    doReturn(succeededFuture(createResults(List.of(existingPendingPayment))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(pendingPaymentCriterion.toString())));

    Criterion encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(encumbranceCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify pending payment update
          ArgumentCaptor<String> tableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(tableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> tableNames = tableNamesCaptor.getAllValues();
          List<List<Object>> entities = updateEntitiesCaptor.getAllValues();
          assertThat(tableNames.get(0), equalTo(TRANSACTIONS_TABLE));

          Transaction savedPendingPayment = (Transaction)(entities.get(0).get(0));
          assertThat(savedPendingPayment.getTransactionType(), equalTo(PENDING_PAYMENT));
          assertNotNull(savedPendingPayment.getMetadata().getUpdatedDate());
          assertThat(savedPendingPayment.getAmount(), equalTo(10d));

          // Verify encumbrance update
          Transaction savedEncumbrance = (Transaction)(entities.get(0).get(1));
          assertThat(savedEncumbrance.getTransactionType(), equalTo(ENCUMBRANCE));
          assertThat(savedEncumbrance.getEncumbrance().getStatus(), equalTo(RELEASED));
          assertNotNull(savedEncumbrance.getMetadata().getUpdatedDate());
          assertThat(savedEncumbrance.getAmount(), equalTo(0d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(10d));

          // Verify budget update
          assertThat(tableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(entities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(10d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCancelPendingPaymentWithLinkedEncumbrance(VertxTestContext testContext) {
    String pendingPaymentId = UUID.randomUUID().toString();
    String encumbranceId = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

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
        .withReleaseEncumbrance(true))
      .withMetadata(new Metadata());

    Transaction newPendingPayment = JsonObject.mapFrom(existingPendingPayment).mapTo(Transaction.class);
    newPendingPayment.setInvoiceCancelled(true);

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
    batch.getTransactionsToUpdate().add(newPendingPayment);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 0d, 0d, false, false, false);

    Criterion pendingPaymentCriterion = createCriterionByIds(List.of(pendingPaymentId));
    doReturn(succeededFuture(createResults(List.of(existingPendingPayment))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(pendingPaymentCriterion.toString())));

    Criterion encumbranceCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(encumbranceCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify pending payment update
          ArgumentCaptor<String> tableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(tableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> tableNames = tableNamesCaptor.getAllValues();
          List<List<Object>> entities = updateEntitiesCaptor.getAllValues();
          assertThat(tableNames.get(0), equalTo(TRANSACTIONS_TABLE));

          Transaction savedPendingPayment = (Transaction)(entities.get(0).get(0));
          assertThat(savedPendingPayment.getTransactionType(), equalTo(PENDING_PAYMENT));
          assertNotNull(savedPendingPayment.getMetadata().getUpdatedDate());
          assertThat(savedPendingPayment.getAmount(), equalTo(0d));
          assertThat(savedPendingPayment.getVoidedAmount(), equalTo(5d));

          // Verify encumbrance update
          Transaction savedEncumbrance = (Transaction)(entities.get(0).get(1));
          assertThat(savedEncumbrance.getTransactionType(), equalTo(ENCUMBRANCE));
          assertThat(savedEncumbrance.getEncumbrance().getStatus(), equalTo(RELEASED));
          assertNotNull(savedEncumbrance.getMetadata().getUpdatedDate());
          assertThat(savedEncumbrance.getAmount(), equalTo(0d));
          assertThat(savedEncumbrance.getEncumbrance().getAmountAwaitingPayment(), equalTo(0d));

          // Verify budget update
          assertThat(tableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(entities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(0d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testExpenditureRestrictionsWhenCreatingTwoPendingPayments(VertxTestContext testContext) {
    String pendingPaymentId1 = UUID.randomUUID().toString();
    String pendingPaymentId2 = UUID.randomUUID().toString();
    String invoiceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String currency = "USD";

    Transaction pendingPayment1 = new Transaction()
      .withId(pendingPaymentId1)
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(7d)
      .withCurrency(currency)
      .withInvoiceCancelled(false);

    Transaction pendingPayment2 = new Transaction()
      .withId(pendingPaymentId2)
      .withTransactionType(PENDING_PAYMENT)
      .withSourceInvoiceId(invoiceId)
      .withFromFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withAmount(8d)
      .withCurrency(currency)
      .withInvoiceCancelled(false);

    Batch batch = new Batch();
    batch.getTransactionsToCreate().addAll(List.of(pendingPayment1, pendingPayment2));

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 0d, 0d, true, false, false);

    Criterion pendingPaymentCriterion = createCriterionByIds(List.of(pendingPaymentId1, pendingPaymentId2));
    doReturn(succeededFuture(createResults(List.of())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(pendingPaymentCriterion.toString())));

    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onFailure(thrown -> {
        HttpException exception = (HttpException)thrown;
        testContext.verify(() -> {
          assertThat(exception.getCode(), equalTo(422));
          assertThat(exception.getErrors().getErrors().get(0).getMessage(),
            equalTo(BUDGET_RESTRICTED_EXPENDITURES_ERROR.getDescription()));
        });
        testContext.completeNow();
      });
  }

}
