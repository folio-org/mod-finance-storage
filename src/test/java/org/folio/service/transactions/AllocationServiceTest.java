package org.folio.service.transactions;

import static org.folio.rest.impl.TestBase.getFile;
import static org.folio.rest.impl.TransactionTest.ALLOCATION_SAMPLE;
import static org.folio.rest.util.ErrorCodes.MISSING_FUND_ID;
import static org.folio.rest.util.ErrorCodes.MUST_BE_POSITIVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.vertx.ext.web.handler.HttpException;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;
import org.folio.service.budget.BudgetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import java.util.function.Function;

@ExtendWith(VertxExtension.class)
public class AllocationServiceTest {

  private AutoCloseable mockitoMocks;
  @InjectMocks
  private AllocationService allocationService;
  @Mock
  private BudgetService budgetService;
  @Mock
  private RequestContext requestContext;
  @Mock
  private DBClient dbClient;
  @Mock
  private DBConn conn;

  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void shouldThrowExceptionWithMissingFundIdCodeWhenCreateAllocationWithEmptyFromFundIdEndToFundId(VertxTestContext testContext) {

    JsonObject jsonTx = new JsonObject(getFile(ALLOCATION_SAMPLE));
    jsonTx.remove("toFundId");
    jsonTx.remove("fromFundId");
    Transaction transactionSample = jsonTx.mapTo(Transaction.class);
    testContext.assertFailure(allocationService.createTransaction(transactionSample, requestContext))
      .onFailure(thrown -> {
        testContext.verify(() -> {
          assertThat(thrown, instanceOf(HttpException.class));
          assertThat(((HttpException) thrown).getPayload(), containsString(MISSING_FUND_ID.getCode()));
        });
        testContext.completeNow();
      });

  }

  @Test
  void shouldThrowExceptionWithMustBePositiveCodeWhenCreateAllocationWithNegativeAmount(VertxTestContext testContext) {
    JsonObject jsonTx = new JsonObject(getFile(ALLOCATION_SAMPLE));
    Transaction transactionSample = jsonTx.mapTo(Transaction.class);
    transactionSample.setAmount(-10d);
    testContext.assertFailure(allocationService.createTransaction(transactionSample, requestContext))
      .onFailure(thrown -> {
        testContext.verify(() -> {
          assertThat(thrown, instanceOf(HttpException.class));
          assertThat(((HttpException) thrown).getPayload(), containsString(MUST_BE_POSITIVE.getCode()));
        });
        testContext.completeNow();
      });

  }

  @Test
  void shouldCreateAllocationAndUpdateBudgetSpecifiedInFromFundIdWhenCreateAllocationAndToFundIdNotSpecified(
      VertxTestContext testContext) {
    JsonObject jsonTx = new JsonObject(getFile(ALLOCATION_SAMPLE));
    Transaction transactionSample = jsonTx.mapTo(Transaction.class);
    transactionSample.setToFundId(null);
    transactionSample.setAmount(40d);

    when(requestContext.toDBClient())
      .thenReturn(dbClient);
    doAnswer(invocation -> {
      Function<DBConn, Future<Transaction>> function = invocation.getArgument(0);
      return function.apply(conn);
    }).when(dbClient).withTrans(any());
    when(budgetService.checkBudgetHaveMoneyForTransaction(any(), any()))
      .thenReturn(Future.succeededFuture());
    doReturn(Future.succeededFuture(transactionSample.getId()))
      .when(conn).save(any(), any(), any());
    when(budgetService.getBudgetByFiscalYearIdAndFundIdForUpdate(anyString(), anyString(), any()))
      .thenReturn(Future.succeededFuture(new Budget().withInitialAllocation(50d)));
    doNothing().when(budgetService)
      .updateBudgetMetadata(any(), any());
    when(budgetService.updateBatchBudgets(any(), any()))
      .thenReturn(Future.succeededFuture(1));

    testContext.assertComplete(allocationService.createTransaction(transactionSample, requestContext))
      .onSuccess(transaction -> {
        testContext.verify(() -> {
          ArgumentCaptor<Budget> argumentCaptor = ArgumentCaptor.forClass(Budget.class);
          verify(budgetService).updateBudgetMetadata(argumentCaptor.capture(), any());
          Budget budget = argumentCaptor.getValue();
          assertEquals(40d, budget.getAllocationFrom());
        });
        testContext.completeNow();
      });

  }
}
