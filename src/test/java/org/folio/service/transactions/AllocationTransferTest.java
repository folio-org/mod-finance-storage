package org.folio.service.transactions;

import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Tuple;
import io.vertx.ext.web.handler.HttpException;
import org.folio.rest.jaxrs.model.Batch;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.Criteria.Criterion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.dao.ledger.LedgerPostgresDAO.LEDGER_TABLE;
import static org.folio.dao.transactions.BatchTransactionDAO.TRANSACTIONS_TABLE;
import static org.folio.rest.impl.BudgetAPI.BUDGET_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.jaxrs.model.Budget.BudgetStatus.ACTIVE;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.ALLOCATION;
import static org.folio.rest.jaxrs.model.Transaction.TransactionType.TRANSFER;
import static org.hamcrest.CoreMatchers.startsWith;
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

public class AllocationTransferTest extends BatchTransactionServiceTestBase {

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

  @Test
  void testCreateAllocationWithMissingSourceBudget(VertxTestContext testContext) {
    String fundId1 = UUID.randomUUID().toString();
    String fundId2 = UUID.randomUUID().toString();
    String budgetId2 = UUID.randomUUID().toString();
    String transactionId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    String tenantId = "tenantname";
    String ledgerId = UUID.randomUUID().toString();

    Transaction allocation = new Transaction()
      .withId(transactionId)
      .withCurrency("USD")
      .withFromFundId(fundId1)
      .withToFundId(fundId2)
      .withTransactionType(ALLOCATION)
      .withAmount(5d)
      .withFiscalYearId(fiscalYearId);

    Ledger ledger = new Ledger()
      .withId(ledgerId);

    Fund fund1 = new Fund()
      .withId(fundId1)
      .withLedgerId(ledgerId);

    Fund fund2 = new Fund()
      .withId(fundId2)
      .withLedgerId(ledgerId);

    Budget budget2 = new Budget()
      .withId(budgetId2)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId2)
      .withBudgetStatus(ACTIVE)
      .withInitialAllocation(10d)
      .withNetTransfers(0d)
      .withMetadata(new Metadata());

    doReturn(tenantId)
      .when(conn).getTenantId();

    Criterion fundCriterion = createCriterionByIds(List.of(fundId1, fundId2));
    doReturn(succeededFuture(createResults(List.of(fund1, fund2))))
      .when(conn).get(eq(FUND_TABLE), eq(Fund.class), argThat(
        crit -> crit.toString().equals(fundCriterion.toString())), eq(false));

    String sql = "SELECT jsonb FROM " + tenantId + "_mod_finance_storage.budget WHERE (fiscalYearId = '" + fiscalYearId + "' AND (fundId = '%s' OR fundId = '%s')) FOR UPDATE";
    doReturn(succeededFuture(createRowSet(Collections.singletonList(budget2))))
      .when(conn).execute(argThat(s ->
          s.equals(String.format(sql, fundId1, fundId2)) || s.equals(String.format(sql, fundId2, fundId1))),
        any(Tuple.class));

    Criterion ledgerCriterion = createCriterionByIds(List.of(ledgerId));
    doReturn(succeededFuture(createResults(List.of(ledger))))
      .when(conn).get(eq(LEDGER_TABLE), eq(Ledger.class), argThat(
        crit -> crit.toString().equals(ledgerCriterion.toString())), eq(false));

    Batch batch = new Batch();
    batch.getTransactionsToCreate().add(allocation);

    Criterion transactionCriterion = createCriterionByIds(List.of(transactionId));
    doReturn(succeededFuture(createResults(new ArrayList<Transaction>())))
      .when(conn).get(eq(TRANSACTIONS_TABLE), eq(Transaction.class), argThat(
        crit -> crit.toString().equals(transactionCriterion.toString())));

    testContext.assertFailure(batchTransactionService.processBatch(batch, requestContext))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertThat(event.cause(), instanceOf(HttpException.class));
          HttpException exception = (HttpException)event.cause();
          assertThat(exception.getStatusCode(), equalTo(500));
          assertThat(exception.getPayload(), startsWith("Could not find some budgets in the database"));
        });
        testContext.completeNow();
      });
  }


}
