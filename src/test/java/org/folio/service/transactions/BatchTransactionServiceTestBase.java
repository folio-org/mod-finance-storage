package org.folio.service.transactions;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.pgclient.impl.RowImpl;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.impl.RowDesc;
import org.folio.dao.budget.BudgetDAO;
import org.folio.dao.budget.BudgetPostgresDAO;
import org.folio.dao.fund.FundDAO;
import org.folio.dao.fund.FundPostgresDAO;
import org.folio.dao.ledger.LedgerDAO;
import org.folio.dao.ledger.LedgerPostgresDAO;
import org.folio.dao.transactions.BatchTransactionDAO;
import org.folio.dao.transactions.BatchTransactionPostgresDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.CriterionBuilder;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.helpers.LocalRowDesc;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.folio.rest.persist.interfaces.Results;
import org.folio.service.budget.BudgetService;
import org.folio.service.fund.FundService;
import org.folio.service.fund.StorageFundService;
import org.folio.service.ledger.LedgerService;
import org.folio.service.ledger.StorageLedgerService;
import org.folio.service.transactions.batch.BatchAllocationService;
import org.folio.service.transactions.batch.BatchEncumbranceService;
import org.folio.service.transactions.batch.BatchPaymentCreditService;
import org.folio.service.transactions.batch.BatchPendingPaymentService;
import org.folio.service.transactions.batch.BatchTransactionService;
import org.folio.service.transactions.batch.BatchTransactionServiceInterface;
import org.folio.service.transactions.batch.BatchTransferService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.dao.ledger.LedgerPostgresDAO.LEDGER_TABLE;
import static org.folio.rest.impl.FundAPI.FUND_TABLE;
import static org.folio.rest.jaxrs.model.Budget.BudgetStatus.ACTIVE;
import static org.folio.rest.jaxrs.model.Budget.BudgetStatus.INACTIVE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@ExtendWith(VertxExtension.class)
public abstract class BatchTransactionServiceTestBase {
  private AutoCloseable mockitoMocks;
  protected BatchTransactionService batchTransactionService;

  @Mock
  private DBClientFactory dbClientFactory;
  @Mock
  protected RequestContext requestContext;
  @Mock
  private DBClient dbClient;
  @Mock
  protected DBConn conn;
  @Captor
  protected ArgumentCaptor<List<Object>> saveEntitiesCaptor;
  @Captor
  protected ArgumentCaptor<List<Object>> updateEntitiesCaptor;


  @BeforeEach
  public void init() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    FundDAO fundDAO = new FundPostgresDAO();
    FundService fundService = new StorageFundService(fundDAO);
    BudgetDAO budgetDAO = new BudgetPostgresDAO();
    BudgetService budgetService = new BudgetService(budgetDAO);
    LedgerDAO ledgerDAO = new LedgerPostgresDAO();
    LedgerService ledgerService = new StorageLedgerService(ledgerDAO, fundService);
    Set<BatchTransactionServiceInterface> batchTransactionStrategies = new HashSet<>();
    batchTransactionStrategies.add(new BatchEncumbranceService());
    batchTransactionStrategies.add(new BatchPendingPaymentService());
    batchTransactionStrategies.add(new BatchPaymentCreditService());
    batchTransactionStrategies.add(new BatchAllocationService());
    batchTransactionStrategies.add(new BatchTransferService());
    BatchTransactionDAO transactionDAO = new BatchTransactionPostgresDAO();
    batchTransactionService = new BatchTransactionService(dbClientFactory, transactionDAO, fundService, budgetService,
      ledgerService, batchTransactionStrategies);
    doReturn(dbClient)
      .when(dbClientFactory).getDbClient(requestContext);
    doAnswer(invocation -> {
      Function<DBConn, Future<Void>> function = invocation.getArgument(0);
      return function.apply(conn);
    }).when(dbClient).withTrans(any());
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  protected void setupFundBudgetLedger(String fundId, String fiscalYearId, double budgetEncumbered,
      double budgetAwaitingPayment, double budgetExpenditures, boolean restrictExpenditures,
      boolean restrictEncumbrance, boolean inactiveBudget) {
    String tenantId = "tenantname";
    String budgetId = UUID.randomUUID().toString();
    String ledgerId = UUID.randomUUID().toString();

    Fund fund = new Fund()
      .withId(fundId)
      .withCode("FUND1")
      .withLedgerId(ledgerId);

    Budget budget = new Budget()
      .withId(budgetId)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withBudgetStatus(inactiveBudget ? INACTIVE : ACTIVE)
      .withInitialAllocation(10d)
      .withEncumbered(budgetEncumbered)
      .withAwaitingPayment(budgetAwaitingPayment)
      .withExpenditures(budgetExpenditures)
      .withAllowableExpenditure(90d)
      .withAllowableEncumbrance(90d)
      .withMetadata(new Metadata());

    Ledger ledger = new Ledger()
      .withId(ledgerId)
      .withRestrictExpenditures(restrictExpenditures)
      .withRestrictEncumbrance(restrictEncumbrance);

    doReturn(tenantId)
      .when(conn).getTenantId();

    Criterion fundCriterion = createCriterionByIds(List.of(fundId));
    doReturn(succeededFuture(createResults(List.of(fund))))
      .when(conn).get(eq(FUND_TABLE), eq(Fund.class), argThat(
        crit -> crit.toString().equals(fundCriterion.toString())), eq(false));

    String sql = "SELECT jsonb FROM " + tenantId + "_mod_finance_storage.budget WHERE (fiscalYearId = '" + fiscalYearId + "' AND (fundId = '" + fundId + "')) FOR UPDATE";
    doReturn(succeededFuture(createRowSet(List.of(budget))))
      .when(conn).execute(eq(sql), any(Tuple.class));

    Criterion ledgerCriterion = createCriterionByIds(List.of(ledgerId));
    doReturn(succeededFuture(createResults(List.of(ledger))))
      .when(conn).get(eq(LEDGER_TABLE), eq(Ledger.class), argThat(
        crit -> crit.toString().equals(ledgerCriterion.toString())), eq(false));
  }

  protected void setup2Funds2Budgets1Ledger(String fundId1, String fundId2, String budgetId1, String budgetId2,
    String fiscalYearId) {
    String tenantId = "tenantname";
    String ledgerId = UUID.randomUUID().toString();
    Ledger ledger = new Ledger()
      .withId(ledgerId);

    Fund fund1 = new Fund()
      .withId(fundId1)
      .withLedgerId(ledgerId);

    Fund fund2 = new Fund()
      .withId(fundId2)
      .withLedgerId(ledgerId);

    Budget budget1 = new Budget()
      .withId(budgetId1)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId1)
      .withBudgetStatus(ACTIVE)
      .withInitialAllocation(5d)
      .withMetadata(new Metadata());

    Budget budget2 = new Budget()
      .withId(budgetId2)
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId2)
      .withBudgetStatus(ACTIVE)
      .withInitialAllocation(10d)
      .withMetadata(new Metadata());

    doReturn(tenantId)
      .when(conn).getTenantId();

    Criterion fundCriterion = createCriterionByIds(List.of(fundId1, fundId2));
    doReturn(succeededFuture(createResults(List.of(fund1, fund2))))
      .when(conn).get(eq(FUND_TABLE), eq(Fund.class), argThat(
        crit -> crit.toString().equals(fundCriterion.toString())), eq(false));

    String sql = "SELECT jsonb FROM " + tenantId + "_mod_finance_storage.budget WHERE (fiscalYearId = '" + fiscalYearId + "' AND (fundId = '%s' OR fundId = '%s')) FOR UPDATE";
    doReturn(succeededFuture(createRowSet(List.of(budget1, budget2))))
      .when(conn).execute(argThat(s ->
          s.equals(String.format(sql, fundId1, fundId2)) || s.equals(String.format(sql, fundId2, fundId1))),
        any(Tuple.class));

    Criterion ledgerCriterion = createCriterionByIds(List.of(ledgerId));
    doReturn(succeededFuture(createResults(List.of(ledger))))
      .when(conn).get(eq(LEDGER_TABLE), eq(Ledger.class), argThat(
        crit -> crit.toString().equals(ledgerCriterion.toString())), eq(false));
  }

  protected void setupFundWithMissingBudget(String fundId, String fiscalYearId) {
    String tenantId = "tenantname";
    String ledgerId = UUID.randomUUID().toString();

    Fund fund = new Fund()
      .withId(fundId)
      .withLedgerId(ledgerId);

    Ledger ledger = new Ledger()
      .withId(ledgerId)
      .withRestrictExpenditures(false)
      .withRestrictEncumbrance(false);

    doReturn(tenantId)
      .when(conn).getTenantId();

    Criterion fundCriterion = createCriterionByIds(List.of(fundId));
    doReturn(succeededFuture(createResults(List.of(fund))))
      .when(conn).get(eq(FUND_TABLE), eq(Fund.class), argThat(
        crit -> crit.toString().equals(fundCriterion.toString())), eq(false));

    String sql = "SELECT jsonb FROM " + tenantId + "_mod_finance_storage.budget WHERE (fiscalYearId = '" + fiscalYearId + "' AND (fundId = '" + fundId + "')) FOR UPDATE";
    doReturn(succeededFuture(createRowSet(Collections.emptyList())))
      .when(conn).execute(eq(sql), any(Tuple.class));

    Criterion ledgerCriterion = createCriterionByIds(List.of(ledgerId));
    doReturn(succeededFuture(createResults(List.of(ledger))))
      .when(conn).get(eq(LEDGER_TABLE), eq(Ledger.class), argThat(
        crit -> crit.toString().equals(ledgerCriterion.toString())), eq(false));
  }

  protected Criterion createCriterionByIds(List<String> ids) {
    CriterionBuilder criterionBuilder = new CriterionBuilder("OR");
    ids.forEach(id -> criterionBuilder.with("id", id));
    return criterionBuilder.build();
  }

  protected <T> Results<T> createResults(List<T> list) {
    Results<T> results = new Results<>();
    results.setResults(list);
    return results;
  }

  protected <T> RowSet<Row> createRowSet(List<T> list) {
    RowDesc rowDesc = new LocalRowDesc(List.of("foo"));
    List<Row> rows = list.stream().map(item -> {
      Row row = new RowImpl(rowDesc);
      row.addJsonObject(JsonObject.mapFrom(item));
      return row;
    }).toList();
    return new LocalRowSet(list.size())
      .withRows(rows);
  }

}
