package org.folio.service.transactions;

import io.vertx.junit5.VertxTestContext;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.dao.transactions.BatchTransactionDAO.TRANSACTIONS_TABLE;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ALLOCATION;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.TRANSFER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AllocationCreditTest extends BatchTransactionServiceTestBase {

  @Test
  void testCreateInitialAllocation(VertxTestContext testContext) {
    String transactionId = UUID.randomUUID().toString();
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();

    Transaction allocation = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withToFundId(fundId)
      .withTransactionType(ALLOCATION)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId);

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(allocation);

    setupFundBudgetLedger(fundId, fiscalYearId, 0d, 0d, 0d, false, false, false);

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
          // Verify allocation creation
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();
          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(saveEntities.get(0).get(0));
          assertNotNull(savedTransaction.getMetadata());
          assertThat(savedTransaction.getAmount(), equalTo(allocation.getAmount()));

          // Verify budget update
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          assertThat(updateTableNames.get(0), equalTo(BUDGET_TABLE));
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();
          Budget savedBudget = (Budget)(updateEntities.get(0).get(0));
          assertNotNull(savedBudget.getMetadata().getUpdatedDate());
          assertThat(savedBudget.getInitialAllocation(), equalTo(10d));
          assertThat(savedBudget.getAllocationTo(), equalTo(5d));

        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreateAllocation(VertxTestContext testContext) {
    String fundId1 = UUID.randomUUID().toString();
    String fundId2 = UUID.randomUUID().toString();
    String budgetId1 = UUID.randomUUID().toString();
    String budgetId2 = UUID.randomUUID().toString();
    String transactionId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();

    Transaction allocation = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId1)
      .withToFundId(fundId2)
      .withTransactionType(ALLOCATION)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId);

    setup2Funds2Budgets1Ledger(fundId1, fundId2, budgetId1, budgetId2, fiscalYearId);

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(allocation);

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
          // Verify allocation creation
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();
          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(saveEntities.get(0).get(0));
          assertNotNull(savedTransaction.getMetadata());
          assertThat(savedTransaction.getAmount(), equalTo(allocation.getAmount()));

          // Verify budget updates
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          assertThat(updateTableNames.get(0), equalTo(BUDGET_TABLE));
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();
          Budget savedBudget1 = (Budget)(updateEntities.get(0).get(0));
          assertThat(savedBudget1.getId(), equalTo(budgetId1));
          assertNotNull(savedBudget1.getMetadata().getUpdatedDate());
          assertThat(savedBudget1.getAllocationFrom(), equalTo(5d));
          Budget savedBudget2 = (Budget)(updateEntities.get(0).get(1));
          assertThat(savedBudget2.getId(), equalTo(budgetId2));
          assertNotNull(savedBudget2.getMetadata().getUpdatedDate());
          assertThat(savedBudget2.getAllocationTo(), equalTo(5d));
        });
        testContext.completeNow();
      });
  }

  @Test
  void testCreateTransfer(VertxTestContext testContext) {
    String fundId1 = UUID.randomUUID().toString();
    String fundId2 = UUID.randomUUID().toString();
    String budgetId1 = UUID.randomUUID().toString();
    String budgetId2 = UUID.randomUUID().toString();
    String transactionId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();

    Transaction transfer = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId1)
      .withToFundId(fundId2)
      .withTransactionType(TRANSFER)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId);

    setup2Funds2Budgets1Ledger(fundId1, fundId2, budgetId1, budgetId2, fiscalYearId);

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(transfer);

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
          // Verify allocation creation
          ArgumentCaptor<String> saveTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).saveBatch(saveTableNamesCaptor.capture(), saveEntitiesCaptor.capture());
          List<String> saveTableNames = saveTableNamesCaptor.getAllValues();
          List<List<Object>> saveEntities = saveEntitiesCaptor.getAllValues();
          assertThat(saveTableNames.get(0), equalTo(TRANSACTIONS_TABLE));
          Transaction savedTransaction = (Transaction)(saveEntities.get(0).get(0));
          assertNotNull(savedTransaction.getMetadata());
          assertThat(savedTransaction.getAmount(), equalTo(transfer.getAmount()));

          // Verify budget updates
          ArgumentCaptor<String> updateTableNamesCaptor = ArgumentCaptor.forClass(String.class);
          verify(conn, times(1)).updateBatch(updateTableNamesCaptor.capture(), updateEntitiesCaptor.capture());
          List<String> updateTableNames = updateTableNamesCaptor.getAllValues();
          assertThat(updateTableNames.get(0), equalTo(BUDGET_TABLE));
          List<List<Object>> updateEntities = updateEntitiesCaptor.getAllValues();
          Budget savedBudget1 = (Budget)(updateEntities.get(0).get(0));
          assertThat(savedBudget1.getId(), equalTo(budgetId1));
          assertNotNull(savedBudget1.getMetadata().getUpdatedDate());
          assertThat(savedBudget1.getNetTransfers(), equalTo(-5d));
          Budget savedBudget2 = (Budget)(updateEntities.get(0).get(1));
          assertThat(savedBudget2.getId(), equalTo(budgetId2));
          assertNotNull(savedBudget2.getMetadata().getUpdatedDate());
          assertThat(savedBudget2.getNetTransfers(), equalTo(5d));
        });
        testContext.completeNow();
      });
  }

}
