package org.folio.service.group;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.dao.group.GroupPostgresDAO;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.GroupAPI.GROUP_FUND_FY_TABLE;
import static org.folio.service.ServiceTestUtils.createResults;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(VertxExtension.class)
public class GroupServiceTest {
  private AutoCloseable mockitoMocks;
  private GroupService groupService;

  @Mock
  private DBClientFactory dbClientFactory;
  @Mock
  private DBConn conn;
  @Captor
  private ArgumentCaptor<String> tableNameCaptor;
  @Captor
  private ArgumentCaptor<Criterion> criterionCaptor;
  @Captor
  private ArgumentCaptor<Class<Object>> classCaptor;
  @Captor
  private ArgumentCaptor<List<GroupFundFiscalYear>> gffyListCaptor;


  @BeforeEach
  public void init() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
    GroupPostgresDAO groupDAO = new GroupPostgresDAO();
    groupService = new GroupService(dbClientFactory, groupDAO);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void testUpdateBudgetIdForGroupFundFiscalYears(VertxTestContext testContext) {
    String fundId = UUID.randomUUID().toString();
    String fiscalYearId = UUID.randomUUID().toString();
    Budget budget = new Budget()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId)
      .withFiscalYearId(fiscalYearId);
    GroupFundFiscalYear groupFundFiscalYear1 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withGroupId(UUID.randomUUID().toString());
    GroupFundFiscalYear groupFundFiscalYear2 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFiscalYearId(fiscalYearId)
      .withFundId(fundId)
      .withGroupId(UUID.randomUUID().toString());
    List<GroupFundFiscalYear> gffys = List.of(groupFundFiscalYear1, groupFundFiscalYear2);

    doAnswer(invocation -> succeededFuture(createResults(gffys)))
      .when(conn).get(eq(GROUP_FUND_FY_TABLE), eq(GroupFundFiscalYear.class), any(Criterion.class));
    doAnswer(invocation -> succeededFuture())
      .when(conn).updateBatch(eq(GROUP_FUND_FY_TABLE), anyList());

    testContext.assertComplete(groupService.updateBudgetIdForGroupFundFiscalYears(budget, conn))
      .onComplete(result -> {
        verify(conn, times(1)).get(tableNameCaptor.capture(), classCaptor.capture(), criterionCaptor.capture());
        verify(conn, times(1)).updateBatch(tableNameCaptor.capture(), gffyListCaptor.capture());

        List<String> tableNames = tableNameCaptor.getAllValues();
        assertEquals(2, tableNames.size());
        assertEquals(GROUP_FUND_FY_TABLE, tableNames.get(0));
        assertEquals(GROUP_FUND_FY_TABLE, tableNames.get(1));
        List<Class<Object>> classes = classCaptor.getAllValues();
        assertEquals(1, classes.size());
        assertEquals(GroupFundFiscalYear.class, classes.get(0));
        List<Criterion> criteria = criterionCaptor.getAllValues();
        assertEquals(1, criteria.size());
        assertThat(criteria.get(0).toString(), containsString(
          "fundId = '" + fundId + "' AND fiscalYearId = '" + fiscalYearId + "'"));
        List<List<GroupFundFiscalYear>> gffyLists = gffyListCaptor.getAllValues();
        assertEquals(1, gffyLists.size());
        List<GroupFundFiscalYear> groupFundFiscalYears = gffyLists.get(0);
        assertEquals(2, groupFundFiscalYears.size());
        assertThat(groupFundFiscalYears, everyItem(hasProperty("budgetId", is(budget.getId()))));
        assertEquals(groupFundFiscalYear1.getId(), groupFundFiscalYears.get(0).getId());

        testContext.completeNow();
      });
  }

}
