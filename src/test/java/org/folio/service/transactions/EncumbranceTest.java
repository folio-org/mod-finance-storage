package org.folio.service.transactions;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import org.folio.rest.exception.HttpException;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.model.TransactionPatch;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.dao.transactions.BatchTransactionDAO.TRANSACTIONS_TABLE;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.PENDING;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.RELEASED;
import static org.folio.rest.jaxrs.model.Encumbrance.Status.UNRELEASED;
import static org.folio.rest.jaxrs.model.Transaction.Source.PO_LINE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ENCUMBRANCE;
import static org.folio.rest.util.ErrorCodes.BUDGET_RESTRICTED_ENCUMBRANCE_ERROR;
import static org.folio.service.ServiceTestUtils.createResults;
import static org.folio.service.ServiceTestUtils.createRowSet;
import static org.hamcrest.CoreMatchers.startsWith;
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

public class EncumbranceTest extends BatchTransactionServiceTestBase {

  @Test
  void testCreateEncumbrance(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction encumbrance = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(Encumbrance.Status.PENDING)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(5d));

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(encumbrance);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 0d, 0d, false, false, false);

    Criterion transactionCriterion = createCriterionByIds(List.of(transactionId));
    doReturn(succeededFuture(createResults(new ArrayList<Transaction>())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).saveBatch(anyString(), anyList());

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify encumbrance creation
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();
          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(saveEntities.get(0).get(0));
          assertNotNull(savedTransaction.getMetadata());
          assertThat(savedTransaction.getAmount(), equalTo(encumbrance.getAmount()));

          // Verify budget update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          assertThat(updateTableNames.get(0), equalTo(BUDGET_TABLE));
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();
          Budget savedBudget = (Budget)(updateEntities.get(0).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(5d));
        });
        testContext.completeNow();
      });
  }

  @ParameterizedTest
  @CsvSource({
    "0,0,0,0,0,0",     // zero amount
    "5,5,0,0,0,0",     // zero amount and zero initial budget encumbered
    "5,5,0,5,0,0",     // decrease amount
    "5,5,10,5,5,5",    // increase amount and breach upper limit
    "5,5,10,0,5,5",    // increase amount and breach lower limit
    "5,5,10,10,10,10", // increase amount
  })
  void testUpdateEncumbrance(double oldAmount, double oldInitialEncumbered,
                             double newAmount, double newInitialAmountEncumbered,
                             double expectedAmount, double expectedBudgetEncumbered,
                             VertxTestContext testContext) {
    String encumbranceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(oldAmount)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(Encumbrance.Status.PENDING)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(oldInitialEncumbered))
      .withMetadata(new Metadata());

    Transaction newEncumbrance = JsonObject.mapFrom(existingEncumbrance).mapTo(Transaction.class);
    newEncumbrance.setAmount(newAmount);
    newEncumbrance.getEncumbrance().setInitialAmountEncumbered(newInitialAmountEncumbered);

    Batch batch = new Batch();
    batch.getTransactionsToUpdate().add(newEncumbrance);

    setupFundBudgetLedger(fundId, fiscalYearId, oldAmount, 0d, 0d, 0d, false, false, false);

    Criterion transactionCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify encumbrance update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(updateEntities.get(0).get(0));
          assertNotNull(savedTransaction.getMetadata().getUpdatedDate());
          assertThat(savedTransaction.getAmount(), equalTo(expectedAmount));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(expectedBudgetEncumbered));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testDeleteTransaction(VertxTestContext testContext) {
    String tenantId = "tenantname";
    String encumbranceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    Transaction transaction = new Transaction()
      .withId(encumbranceId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(0d)
      .withFiscalYearId(fiscalYearId)
      .withEncumbrance(new Encumbrance()
        .withStatus(RELEASED));

    Batch batch = new Batch();
    batch.getIdsOfTransactionsToDelete().add(encumbranceId);

    doReturn(tenantId)
      .when(conn).getTenantId();

    Criterion criterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(transaction))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(criterion.toString())));

    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    criterionBuilder.withJson("awaitingPayment.encumbranceId", "=", encumbranceId);
    doReturn(succeededFuture(createResults(List.of())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(criterionBuilder.build().toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(List.of(transaction))))
      .when(conn).delete(anyString(), any(Criterion.class));

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify transaction deletion
          ArgumentCaptor<String> deleteTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          ArgumentCaptor<Criterion> deleteCriterionCaptor = ArgumentCaptor.forClass(Criterion.class);
          verify(conn, times(1)).delete(deleteTableNamesCaptor.capture(), deleteCriterionCaptor.capture());
          List<String> deleteTableNames = deleteTableNamesCaptor.getAllValues();
          List<Criterion> deleteCriterions = deleteCriterionCaptor.getAllValues();

          Criterion expectedCriterion = createCriterionByIds(List.of(encumbranceId));
          assertThat(deleteTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Criterion deleteCriterion = deleteCriterions.get(0);
          assertThat(deleteCriterion.toString(), equalTo(expectedCriterion.toString()));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testPatchTransaction(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    TransactionPatch transactionPatch = new TransactionPatch()
      .withId(transactionId)
      .withAdditionalProperty("encumbrance", new LinkedHashMap<String, String>()
        .put("orderStatus", Encumbrance.OrderStatus.CLOSED.value()));
    Batch batch = new Batch();
    batch.getTransactionPatches().add(transactionPatch);
    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onFailure(thrown -> {
        testContext.verify(() -> {
          assertThat(thrown, instanceOf(HttpException.class));
          assertThat(((HttpException) thrown).getCode(), equalTo(500));
          assertThat(thrown.getMessage(), equalTo("transactionPatches: not implemented"));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testEncumbranceRestrictionsWhenCreatingTwoEncumbrances(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction encumbrance = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(10d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(Encumbrance.Status.PENDING)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(10d));

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(encumbrance);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 0d, 0d, false, true, false);

    Criterion transactionCriterion = createCriterionByIds(List.of(transactionId));
    doReturn(succeededFuture(createResults(new ArrayList<Transaction>())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onFailure(thrown -> {
        HttpException exception = (HttpException)thrown;
        testContext.verify(() -> {
          assertThat(exception.getCode(), equalTo(422));
          assertThat(exception.getErrors().getErrors().get(0).getMessage(),
            equalTo(BUDGET_RESTRICTED_ENCUMBRANCE_ERROR.getDescription()));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreateEncumbranceWithInactiveBudget(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction encumbrance = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(Encumbrance.Status.PENDING)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(5d));

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(encumbrance);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 0d, 0d, false, false, true);

    Criterion transactionCriterion = createCriterionByIds(List.of(transactionId));
    doReturn(succeededFuture(createResults(new ArrayList<Transaction>())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        HttpException exception = (HttpException)event.cause();
        testContext.verify(() -> {
          assertEquals(400, exception.getCode());
          assertEquals("budgetIsNotActiveOrPlanned", exception.getErrors().getErrors().get(0).getCode());
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreateEncumbranceWithMissingBudget(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction encumbrance = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(Encumbrance.Status.PENDING)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(5d));

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(encumbrance);

    setupFundWithMissingBudget(fundId, fiscalYearId);

    Criterion transactionCriterion = createCriterionByIds(List.of(transactionId));
    doReturn(succeededFuture(createResults(new ArrayList<Transaction>())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        assertThat(event.cause(), instanceOf(HttpException.class));
        HttpException exception = (org.folio.rest.exception.HttpException)event.cause();
        testContext.verify(() -> {
          assertEquals(500, exception.getCode());
          assertThat(exception.getMessage(), startsWith("Could not find some budgets in the database"));
        });
        testContext.completeNow();
      });
  }

  @ParameterizedTest
  @CsvSource({
    "0,8,0,8,2,2",  // zero amount
    "0,8,0,0,0,0",  // zero amount and zero initial budget encumbered
    "0,8,10,8,8,8", // increase amount and breach upper limit
    "0,8,-2,8,2,2"  // decrease amount and breach lower limit
  })
  void testUnreleaseEncumbrance(double oldAmount, double oldInitialEncumbered,
                                double newAmount, double newInitialAmountEncumbered,
                                double expectedAmount, double expectedBudgetEncumbered,
                                VertxTestContext testContext) {
    String encumbranceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(oldAmount)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(RELEASED)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(oldInitialEncumbered)
        .withAmountAwaitingPayment(5d)
        .withAmountExpended(1d)
        .withAmountCredited(0d))
      .withMetadata(new Metadata());

    Transaction newEncumbrance = JsonObject.mapFrom(existingEncumbrance).mapTo(Transaction.class);
    newEncumbrance.setAmount(newAmount);
    newEncumbrance.getEncumbrance().setInitialAmountEncumbered(newInitialAmountEncumbered);
    newEncumbrance.getEncumbrance().setStatus(UNRELEASED);

    Batch batch = new Batch();
    batch.getTransactionsToUpdate().add(newEncumbrance);

    setupFundBudgetLedger(fundId, fiscalYearId, oldAmount, 5d, 1d, 1d, false, false, false);

    Criterion transactionCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify encumbrance update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(updateEntities.get(0).get(0));
          assertThat(savedTransaction.getAmount(), equalTo(expectedAmount));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(expectedBudgetEncumbered));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(5d));
          assertThat(savedBudget.getExpenditures(), equalTo(1d));
          assertThat(savedBudget.getCredits(), equalTo(1d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testUnreleasePendingEncumbrance(VertxTestContext testContext) {
    String encumbranceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(0d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(PENDING)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(8d)
        .withAmountAwaitingPayment(5d)
        .withAmountExpended(1d)
        .withAmountCredited(1d))
      .withMetadata(new Metadata());

    Transaction newEncumbrance = JsonObject.mapFrom(existingEncumbrance).mapTo(Transaction.class);
    newEncumbrance.getEncumbrance().setStatus(UNRELEASED);

    Batch batch = new Batch();
    batch.getTransactionsToUpdate().add(newEncumbrance);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 5d, 2d, 2d, false, false, false);

    Criterion transactionCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify encumbrance update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(updateEntities.get(0).get(0));
          assertThat(savedTransaction.getAmount(), equalTo(3d));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(3d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(5d));
          assertThat(savedBudget.getExpenditures(), equalTo(2d));
          assertThat(savedBudget.getCredits(), equalTo(2d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testUnreleasePendingEncumbranceWithNegativeEncumbranceAmount(VertxTestContext testContext) {
    String encumbranceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(0d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(PENDING)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(5d)
        .withAmountAwaitingPayment(50d)
        .withAmountExpended(0d)
        .withAmountCredited(0d))
      .withMetadata(new Metadata());

    Transaction newEncumbrance = JsonObject.mapFrom(existingEncumbrance).mapTo(Transaction.class);
    newEncumbrance.getEncumbrance().setStatus(UNRELEASED);

    Batch batch = new Batch();
    batch.getTransactionsToUpdate().add(newEncumbrance);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 50d, 0d, 0d, false, false, false);

    Criterion transactionCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify encumbrance update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(updateEntities.get(0).get(0));
          assertThat(savedTransaction.getAmount(), equalTo(0d));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(0d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(50d));
          assertThat(savedBudget.getExpenditures(), equalTo(0d));
          assertThat(savedBudget.getCredits(), equalTo(0d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testUnreleasePendingEncumbranceWithExceedingInitialEncumbranceAmount(VertxTestContext testContext) {
    String encumbranceId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String orderId = UUID.randomUUID().toString();

    Transaction existingEncumbrance = new Transaction()
      .withId(encumbranceId)
      .withCurrency("USD")
      .withFromFundId(fundId)
      .withTransactionType(ENCUMBRANCE)
      .withAmount(0d)
      .withFiscalYearId(fiscalYearId)
      .withSource(PO_LINE)
      .withEncumbrance(new Encumbrance()
        .withStatus(PENDING)
        .withSourcePurchaseOrderId(orderId)
        .withInitialAmountEncumbered(5d)
        .withAmountAwaitingPayment(50d)
        .withAmountExpended(0d)
        .withAmountCredited(55d))
      .withMetadata(new Metadata());

    Transaction newEncumbrance = JsonObject.mapFrom(existingEncumbrance).mapTo(Transaction.class);
    newEncumbrance.getEncumbrance().setStatus(UNRELEASED);

    Batch batch = new Batch();
    batch.getTransactionsToUpdate().add(newEncumbrance);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 50d, 55d, 0d, false, false, false);

    Criterion transactionCriterion = createCriterionByIds(List.of(encumbranceId));
    doReturn(succeededFuture(createResults(List.of(existingEncumbrance))))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    doAnswer(invocation -> succeededFuture(createRowSet(invocation.getArgument(1))))
      .when(conn).updateBatch(anyString(), anyList());

    testContext.assertComplete(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          // Verify encumbrance update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(2)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();

          assertThat(updateTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(updateEntities.get(0).get(0));
          assertThat(savedTransaction.getAmount(), equalTo(5d));

          // Verify budget update
          assertThat(updateTableNames.get(1), equalTo(BUDGET_TABLE));
          Budget savedBudget = (Budget)(updateEntities.get(1).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getEncumbered(), equalTo(5d));
          assertThat(savedBudget.getAwaitingPayment(), equalTo(50d));
          assertThat(savedBudget.getExpenditures(), equalTo(0d));
          assertThat(savedBudget.getCredits(), equalTo(55d));
        });
        testContext.completeNow();
      });
  }
}
