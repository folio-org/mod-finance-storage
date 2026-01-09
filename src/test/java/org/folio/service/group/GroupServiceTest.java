package org.folio.service.group;

import io.vertx.core.Context;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.dao.group.GroupPostgresDAO;
import org.folio.rest.core.model.RequestContext;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.jaxrs.model.GroupFundFiscalYearBatchRequest;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBClientFactory;
import org.folio.rest.persist.DBConn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.impl.GroupAPI.GROUP_FUND_FY_TABLE;
import static org.folio.service.ServiceTestUtils.createResults;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class GroupServiceTest {

  private AutoCloseable mockitoMocks;
  private GroupService groupService;

  @Mock
  private DBClientFactory dbClientFactory;
  @Mock
  private DBConn conn;
  @Mock
  private DBClient dbClient;
  @Mock
  private Context vertxContext;
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
        assertEquals(GROUP_FUND_FY_TABLE, tableNames.getFirst());
        assertEquals(GROUP_FUND_FY_TABLE, tableNames.get(1));
        List<Class<Object>> classes = classCaptor.getAllValues();
        assertEquals(1, classes.size());
        assertEquals(GroupFundFiscalYear.class, classes.getFirst());
        List<Criterion> criteria = criterionCaptor.getAllValues();
        assertEquals(1, criteria.size());
        assertThat(criteria.getFirst().toString(), containsString(
          "fundId = '" + fundId + "' AND fiscalYearId = '" + fiscalYearId + "'"));
        List<List<GroupFundFiscalYear>> gffyLists = gffyListCaptor.getAllValues();
        assertEquals(1, gffyLists.size());
        List<GroupFundFiscalYear> groupFundFiscalYears = gffyLists.getFirst();
        assertEquals(2, groupFundFiscalYears.size());
        assertThat(groupFundFiscalYears, everyItem(hasProperty("budgetId", is(budget.getId()))));
        assertEquals(groupFundFiscalYear1.getId(), groupFundFiscalYears.getFirst().getId());

        testContext.completeNow();
      });
  }

  @ParameterizedTest
  @MethodSource("batchRequestSuccessScenarios")
  @SuppressWarnings("unchecked")
  void testGetGroupFundFiscalYearsByFundIds_Success(String testName, List<GroupFundFiscalYear> allRecords,
                                                     GroupFundFiscalYearBatchRequest request, int expectedCount,
                                                     VertxTestContext testContext) {
    // Setup mocks
    when(dbClientFactory.getDbClient(any(RequestContext.class))).thenReturn(dbClient);
    doAnswer(invocation -> {
      var function = invocation.getArgument(0, java.util.function.Function.class);
      return function.apply(conn);
    }).when(dbClient).withConn(any());

    doAnswer(invocation -> succeededFuture(createResults(allRecords)))
      .when(conn).get(eq(GROUP_FUND_FY_TABLE), eq(GroupFundFiscalYear.class), any(Criterion.class));

    // Execute
    var requestContext = new RequestContext(vertxContext, new HashMap<>());
    testContext.assertComplete(groupService.getGroupFundFiscalYearsByFundIds(request, requestContext))
      .onComplete(result -> {
        assertNotNull(result.result());
        var collection = result.result();

        assertEquals(expectedCount, collection.getTotalRecords(),
          "Total records should match expected count for: " + testName);
        assertThat(collection.getGroupFundFiscalYears(), hasSize(expectedCount));

        // Verify all returned records have fund IDs in the request
        if (!collection.getGroupFundFiscalYears().isEmpty()) {
          var fundIds = request.getFundIds();
          collection.getGroupFundFiscalYears().forEach(gffy ->
            assertThat("Fund ID should be in request", fundIds.contains(gffy.getFundId()), is(true)));
        }

        testContext.completeNow();
      });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetGroupFundFiscalYearsByFundIds_WithFiscalYearFilter(VertxTestContext testContext) {
    var fiscalYearId = UUID.randomUUID().toString();
    var fundId1 = UUID.randomUUID().toString();
    var fundId2 = UUID.randomUUID().toString();
    var groupId = UUID.randomUUID().toString();

    var gffy1 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId1)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId);

    var gffy2 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId2)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId);

    var matchingRecords = List.of(gffy1, gffy2);

    when(dbClientFactory.getDbClient(any(RequestContext.class))).thenReturn(dbClient);
    doAnswer(invocation -> {
      var function = invocation.getArgument(0, java.util.function.Function.class);
      return function.apply(conn);
    }).when(dbClient).withConn(any());

    doAnswer(invocation -> succeededFuture(createResults(matchingRecords)))
      .when(conn).get(eq(GROUP_FUND_FY_TABLE), eq(GroupFundFiscalYear.class), any(Criterion.class));

    var request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(List.of(fundId1, fundId2))
      .withFiscalYearId(fiscalYearId);

    var requestContext = new RequestContext(vertxContext, new HashMap<>());

    testContext.assertComplete(groupService.getGroupFundFiscalYearsByFundIds(request, requestContext))
      .onComplete(result -> {
        assertNotNull(result.result());
        var collection = result.result();

        assertEquals(2, collection.getTotalRecords());
        assertThat(collection.getGroupFundFiscalYears(), hasSize(2));
        assertThat(collection.getGroupFundFiscalYears(),
          everyItem(hasProperty("fiscalYearId", is(fiscalYearId))));

        testContext.completeNow();
      });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetGroupFundFiscalYearsByFundIds_WithGroupIdFilter(VertxTestContext testContext) {
    var fiscalYearId = UUID.randomUUID().toString();
    var fundId = UUID.randomUUID().toString();
    var groupId = UUID.randomUUID().toString();

    var gffy = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId);

    var matchingRecords = List.of(gffy);

    when(dbClientFactory.getDbClient(any(RequestContext.class))).thenReturn(dbClient);
    doAnswer(invocation -> {
      var function = invocation.getArgument(0, java.util.function.Function.class);
      return function.apply(conn);
    }).when(dbClient).withConn(any());

    doAnswer(invocation -> succeededFuture(createResults(matchingRecords)))
      .when(conn).get(eq(GROUP_FUND_FY_TABLE), eq(GroupFundFiscalYear.class), any(Criterion.class));

    var request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(List.of(fundId))
      .withGroupId(groupId);

    var requestContext = new RequestContext(vertxContext, new HashMap<>());

    testContext.assertComplete(groupService.getGroupFundFiscalYearsByFundIds(request, requestContext))
      .onComplete(result -> {
        assertNotNull(result.result());
        var collection = result.result();

        assertEquals(1, collection.getTotalRecords());
        assertThat(collection.getGroupFundFiscalYears(), hasSize(1));
        assertEquals(groupId, collection.getGroupFundFiscalYears().getFirst().getGroupId());

        testContext.completeNow();
      });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetGroupFundFiscalYearsByFundIds_WithAllFilters(VertxTestContext testContext) {
    var fiscalYearId = UUID.randomUUID().toString();
    var fundId = UUID.randomUUID().toString();
    var groupId = UUID.randomUUID().toString();

    var gffy = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId);

    var matchingRecords = List.of(gffy);

    when(dbClientFactory.getDbClient(any(RequestContext.class))).thenReturn(dbClient);
    doAnswer(invocation -> {
      var function = invocation.getArgument(0, java.util.function.Function.class);
      return function.apply(conn);
    }).when(dbClient).withConn(any());

    doAnswer(invocation -> succeededFuture(createResults(matchingRecords)))
      .when(conn).get(eq(GROUP_FUND_FY_TABLE), eq(GroupFundFiscalYear.class), any(Criterion.class));

    var request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(List.of(fundId))
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId);

    var requestContext = new RequestContext(vertxContext, new HashMap<>());

    testContext.assertComplete(groupService.getGroupFundFiscalYearsByFundIds(request, requestContext))
      .onComplete(result -> {
        assertNotNull(result.result());
        var collection = result.result();

        assertEquals(1, collection.getTotalRecords());
        assertThat(collection.getGroupFundFiscalYears(), hasSize(1));

        var resultRecord = collection.getGroupFundFiscalYears().getFirst();
        assertEquals(fundId, resultRecord.getFundId());
        assertEquals(fiscalYearId, resultRecord.getFiscalYearId());
        assertEquals(groupId, resultRecord.getGroupId());

        testContext.completeNow();
      });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetGroupFundFiscalYearsByFundIds_EmptyFundIds(VertxTestContext testContext) {
    when(dbClientFactory.getDbClient(any(RequestContext.class))).thenReturn(dbClient);
    doAnswer(invocation -> {
      var function = invocation.getArgument(0, java.util.function.Function.class);
      return function.apply(conn);
    }).when(dbClient).withConn(any());

    doAnswer(invocation -> succeededFuture(createResults(Collections.emptyList())))
      .when(conn).get(eq(GROUP_FUND_FY_TABLE), eq(GroupFundFiscalYear.class), any(Criterion.class));

    var request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(new ArrayList<>());

    var requestContext = new RequestContext(vertxContext, new HashMap<>());

    testContext.assertComplete(groupService.getGroupFundFiscalYearsByFundIds(request, requestContext))
      .onComplete(result -> {
        assertNotNull(result.result());
        var collection = result.result();

        assertEquals(0, collection.getTotalRecords());
        assertThat(collection.getGroupFundFiscalYears(), empty());

        testContext.completeNow();
      });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetGroupFundFiscalYearsByFundIds_NonExistentFundIds(VertxTestContext testContext) {
    var nonExistentFundIds = List.of(
      UUID.randomUUID().toString(),
      UUID.randomUUID().toString(),
      UUID.randomUUID().toString()
    );

    when(dbClientFactory.getDbClient(any(RequestContext.class))).thenReturn(dbClient);
    doAnswer(invocation -> {
      var function = invocation.getArgument(0, java.util.function.Function.class);
      return function.apply(conn);
    }).when(dbClient).withConn(any());

    doAnswer(invocation -> succeededFuture(createResults(Collections.emptyList())))
      .when(conn).get(eq(GROUP_FUND_FY_TABLE), eq(GroupFundFiscalYear.class), any(Criterion.class));

    var request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(nonExistentFundIds);

    var requestContext = new RequestContext(vertxContext, new HashMap<>());

    testContext.assertComplete(groupService.getGroupFundFiscalYearsByFundIds(request, requestContext))
      .onComplete(result -> {
        assertNotNull(result.result());
        var collection = result.result();

        assertEquals(0, collection.getTotalRecords());
        assertThat(collection.getGroupFundFiscalYears(), empty());

        testContext.completeNow();
      });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetGroupFundFiscalYearsByFundIds_LargeNumberOfFundIds(VertxTestContext testContext) {
    var fiscalYearId = UUID.randomUUID().toString();
    var groupId = UUID.randomUUID().toString();

    // Create 50 records
    List<GroupFundFiscalYear> allRecords = new ArrayList<>();
    List<String> fundIds = new ArrayList<>();

    for (int i = 0; i < 50; i++) {
      var fundId = UUID.randomUUID().toString();
      fundIds.add(fundId);
      allRecords.add(new GroupFundFiscalYear()
        .withId(UUID.randomUUID().toString())
        .withFundId(fundId)
        .withFiscalYearId(fiscalYearId)
        .withGroupId(groupId));
    }

    when(dbClientFactory.getDbClient(any(RequestContext.class))).thenReturn(dbClient);
    doAnswer(invocation -> {
      var function = invocation.getArgument(0, java.util.function.Function.class);
      return function.apply(conn);
    }).when(dbClient).withConn(any());

    doAnswer(invocation -> succeededFuture(createResults(allRecords)))
      .when(conn).get(eq(GROUP_FUND_FY_TABLE), eq(GroupFundFiscalYear.class), any(Criterion.class));

    var request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(fundIds);

    var requestContext = new RequestContext(vertxContext, new HashMap<>());

    testContext.assertComplete(groupService.getGroupFundFiscalYearsByFundIds(request, requestContext))
      .onComplete(result -> {
        assertNotNull(result.result());
        var collection = result.result();

        assertEquals(50, collection.getTotalRecords());
        assertThat(collection.getGroupFundFiscalYears(), hasSize(50));

        testContext.completeNow();
      });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetGroupFundFiscalYearsByFundIds_MixedExistingAndNonExisting(VertxTestContext testContext) {
    var fiscalYearId = UUID.randomUUID().toString();
    var groupId = UUID.randomUUID().toString();

    var existingFundId1 = UUID.randomUUID().toString();
    var existingFundId2 = UUID.randomUUID().toString();
    var nonExistentFundId1 = UUID.randomUUID().toString();
    var nonExistentFundId2 = UUID.randomUUID().toString();

    var gffy1 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(existingFundId1)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId);

    var gffy2 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(existingFundId2)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId);

    var matchingRecords = List.of(gffy1, gffy2);

    when(dbClientFactory.getDbClient(any(RequestContext.class))).thenReturn(dbClient);
    doAnswer(invocation -> {
      var function = invocation.getArgument(0, java.util.function.Function.class);
      return function.apply(conn);
    }).when(dbClient).withConn(any());

    doAnswer(invocation -> succeededFuture(createResults(matchingRecords)))
      .when(conn).get(eq(GROUP_FUND_FY_TABLE), eq(GroupFundFiscalYear.class), any(Criterion.class));

    var request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(List.of(existingFundId1, existingFundId2, nonExistentFundId1, nonExistentFundId2));

    var requestContext = new RequestContext(vertxContext, new HashMap<>());

    testContext.assertComplete(groupService.getGroupFundFiscalYearsByFundIds(request, requestContext))
      .onComplete(result -> {
        assertNotNull(result.result());
        var collection = result.result();

        assertEquals(2, collection.getTotalRecords());
        assertThat(collection.getGroupFundFiscalYears(), hasSize(2));

        testContext.completeNow();
      });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetGroupFundFiscalYearsByFundIds_MultipleFiscalYearsPerFund(VertxTestContext testContext) {
    var fundId = UUID.randomUUID().toString();
    var fiscalYearId1 = UUID.randomUUID().toString();
    var fiscalYearId2 = UUID.randomUUID().toString();
    var groupId = UUID.randomUUID().toString();

    var gffy1 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId)
      .withFiscalYearId(fiscalYearId1)
      .withGroupId(groupId);

    var gffy2 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId)
      .withFiscalYearId(fiscalYearId2)
      .withGroupId(groupId);

    var allRecords = List.of(gffy1, gffy2);

    when(dbClientFactory.getDbClient(any(RequestContext.class))).thenReturn(dbClient);
    doAnswer(invocation -> {
      var function = invocation.getArgument(0, java.util.function.Function.class);
      return function.apply(conn);
    }).when(dbClient).withConn(any());

    doAnswer(invocation -> succeededFuture(createResults(allRecords)))
      .when(conn).get(eq(GROUP_FUND_FY_TABLE), eq(GroupFundFiscalYear.class), any(Criterion.class));

    var request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(List.of(fundId));

    var requestContext = new RequestContext(vertxContext, new HashMap<>());

    testContext.assertComplete(groupService.getGroupFundFiscalYearsByFundIds(request, requestContext))
      .onComplete(result -> {
        assertNotNull(result.result());
        var collection = result.result();

        assertEquals(2, collection.getTotalRecords());
        assertThat(collection.getGroupFundFiscalYears(), hasSize(2));
        assertThat(collection.getGroupFundFiscalYears(),
          everyItem(hasProperty("fundId", is(fundId))));

        testContext.completeNow();
      });
  }

  @Test
  @SuppressWarnings("unchecked")
  void testGetGroupFundFiscalYearsByFundIds_MultipleGroupsPerFund(VertxTestContext testContext) {
    var fundId = UUID.randomUUID().toString();
    var fiscalYearId = UUID.randomUUID().toString();
    var groupId1 = UUID.randomUUID().toString();
    var groupId2 = UUID.randomUUID().toString();

    var gffy1 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId1);

    var gffy2 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId2);

    var allRecords = List.of(gffy1, gffy2);

    when(dbClientFactory.getDbClient(any(RequestContext.class))).thenReturn(dbClient);
    doAnswer(invocation -> {
      var function = invocation.getArgument(0, java.util.function.Function.class);
      return function.apply(conn);
    }).when(dbClient).withConn(any());

    doAnswer(invocation -> succeededFuture(createResults(allRecords)))
      .when(conn).get(eq(GROUP_FUND_FY_TABLE), eq(GroupFundFiscalYear.class), any(Criterion.class));

    var request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(List.of(fundId));

    var requestContext = new RequestContext(vertxContext, new HashMap<>());

    testContext.assertComplete(groupService.getGroupFundFiscalYearsByFundIds(request, requestContext))
      .onComplete(result -> {
        assertNotNull(result.result());
        var collection = result.result();

        assertEquals(2, collection.getTotalRecords());
        assertThat(collection.getGroupFundFiscalYears(), hasSize(2));
        assertThat(collection.getGroupFundFiscalYears(),
          everyItem(hasProperty("fundId", is(fundId))));

        testContext.completeNow();
      });
  }

  private static Stream<Arguments> batchRequestSuccessScenarios() {
    var fiscalYearId = UUID.randomUUID().toString();
    var groupId = UUID.randomUUID().toString();

    // Scenario 1: Three funds with one record each
    var fundId1 = UUID.randomUUID().toString();
    var fundId2 = UUID.randomUUID().toString();
    var fundId3 = UUID.randomUUID().toString();

    var gffy1 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId1)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId);

    var gffy2 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId2)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId);

    var gffy3 = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(fundId3)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId);

    var scenario1Records = List.of(gffy1, gffy2, gffy3);
    var scenario1Request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(List.of(fundId1, fundId2, fundId3));

    // Scenario 2: Single fund
    var singleFundId = UUID.randomUUID().toString();
    var gffySingle = new GroupFundFiscalYear()
      .withId(UUID.randomUUID().toString())
      .withFundId(singleFundId)
      .withFiscalYearId(fiscalYearId)
      .withGroupId(groupId);

    var scenario2Records = List.of(gffySingle);
    var scenario2Request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(List.of(singleFundId));

    // Scenario 3: Many funds
    var manyFundIds = new ArrayList<String>();
    var manyRecords = new ArrayList<GroupFundFiscalYear>();
    for (int i = 0; i < 10; i++) {
      var fundId = UUID.randomUUID().toString();
      manyFundIds.add(fundId);
      manyRecords.add(new GroupFundFiscalYear()
        .withId(UUID.randomUUID().toString())
        .withFundId(fundId)
        .withFiscalYearId(fiscalYearId)
        .withGroupId(groupId));
    }

    var scenario3Request = new GroupFundFiscalYearBatchRequest()
      .withFundIds(manyFundIds);

    return Stream.of(
      Arguments.of("Three funds", scenario1Records, scenario1Request, 3),
      Arguments.of("Single fund", scenario2Records, scenario2Request, 1),
      Arguments.of("Many funds", manyRecords, scenario3Request, 10)
    );
  }

}
