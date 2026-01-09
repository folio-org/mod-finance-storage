package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.folio.rest.utils.TestEntities.GROUP_FUND_FY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.jaxrs.model.GroupFundFiscalYearBatchRequest;
import org.folio.rest.jaxrs.model.GroupFundFiscalYearCollection;
import org.folio.rest.jaxrs.model.TenantJob;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;

public class GroupFundFYTest extends TestBase {

  @Test
  public void testGetQuery() {
    Header tenantHeader = new Header(OKAPI_HEADER_TENANT, "gffy_query");
    TenantJob tenantJob = prepareTenant(tenantHeader, true, true);

    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint(), GROUP_FUND_FY.getInitialQuantity(), tenantHeader);

    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==NonExistent", 0, tenantHeader);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==History", GROUP_FUND_FY.getInitialQuantity(), tenantHeader);

    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fiscalYear.code==NonExistent", 0, tenantHeader);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fiscalYear.code==FY2026", 12, tenantHeader);

    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.code==NonExistent", 0, tenantHeader);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.fundStatus==Active", 12, tenantHeader);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.name=European", 2, tenantHeader);

    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fundType.name==NonExistent", 0, tenantHeader);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fundType.name==Approvals", 2, tenantHeader);

    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=ledger.name==One-time", 12, tenantHeader);

    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==History and fiscalYear.code==FY2026", 12, tenantHeader);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.code==HIST and fiscalYear.periodEnd < 2027-01-01 and fund.fundStatus==Inactive and ledger.name==One-time", 1, tenantHeader);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.code==HIST and fundType.name==Approvals and ledger.name==One-time", 2, tenantHeader);
    testInvalidCQLQuery(GROUP_FUND_FY.getEndpoint() + "?query=invalid-query");

    purge(tenantHeader);
    deleteTenant(tenantJob, tenantHeader);
  }

  @Test
  public void testBatchEndpointSuccess() {
    Header tenantHeader = new Header(OKAPI_HEADER_TENANT, "gffy_batch_success");
    TenantJob tenantJob = prepareTenant(tenantHeader, true, true);

    GroupFundFiscalYearCollection allRecords = getData(GROUP_FUND_FY.getEndpoint(), tenantHeader)
      .as(GroupFundFiscalYearCollection.class);

    List<String> fundIds = allRecords.getGroupFundFiscalYears().stream()
      .map(GroupFundFiscalYear::getFundId)
      .distinct()
      .limit(3)
      .toList();

    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(fundIds);

    GroupFundFiscalYearCollection result = postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      valueAsString(batchRequest),
      tenantHeader)
      .then()
      .statusCode(200)
      .extract()
      .as(GroupFundFiscalYearCollection.class);

    long expectedCount = allRecords.getGroupFundFiscalYears().stream()
      .filter(gffy -> fundIds.contains(gffy.getFundId()))
      .count();

    assertThat(result.getGroupFundFiscalYears().size(), equalTo((int) expectedCount));
    assertThat(result.getTotalRecords(), equalTo((int) expectedCount));

    result.getGroupFundFiscalYears().forEach(gffy ->
      assertThat("FundId should be in the requested list", fundIds.contains(gffy.getFundId())));

    purge(tenantHeader);
    deleteTenant(tenantJob, tenantHeader);
  }

  @Test
  public void testBatchEndpointWithFilters() {
    Header tenantHeader = new Header(OKAPI_HEADER_TENANT, "gffy_batch_filters");
    TenantJob tenantJob = prepareTenant(tenantHeader, true, true);

    GroupFundFiscalYearCollection allRecords = getData(GROUP_FUND_FY.getEndpoint(), tenantHeader)
      .as(GroupFundFiscalYearCollection.class);

    GroupFundFiscalYear sampleRecord = allRecords.getGroupFundFiscalYears().getFirst();
    List<String> fundIds = List.of(sampleRecord.getFundId());

    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(fundIds)
      .withFiscalYearId(sampleRecord.getFiscalYearId())
      .withGroupId(sampleRecord.getGroupId());

    GroupFundFiscalYearCollection result = postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      valueAsString(batchRequest),
      tenantHeader)
      .then()
      .statusCode(200)
      .extract()
      .as(GroupFundFiscalYearCollection.class);

    assertThat(result.getGroupFundFiscalYears(), hasSize(1));
    assertThat(result.getTotalRecords(), equalTo(1));

    GroupFundFiscalYear resultRecord = result.getGroupFundFiscalYears().getFirst();
    assertThat(resultRecord.getFundId(), equalTo(sampleRecord.getFundId()));
    assertThat(resultRecord.getFiscalYearId(), equalTo(sampleRecord.getFiscalYearId()));
    assertThat(resultRecord.getGroupId(), equalTo(sampleRecord.getGroupId()));

    purge(tenantHeader);
    deleteTenant(tenantJob, tenantHeader);
  }

  @Test
  public void testBatchEndpointWithMultipleFunds() {
    Header tenantHeader = new Header(OKAPI_HEADER_TENANT, "gffy_batch_multi");
    TenantJob tenantJob = prepareTenant(tenantHeader, true, true);

    GroupFundFiscalYearCollection allRecords = getData(GROUP_FUND_FY.getEndpoint(), tenantHeader)
      .as(GroupFundFiscalYearCollection.class);

    List<String> allFundIds = allRecords.getGroupFundFiscalYears().stream()
      .map(GroupFundFiscalYear::getFundId)
      .distinct()
      .toList();

    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(allFundIds);

    GroupFundFiscalYearCollection result = postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      valueAsString(batchRequest),
      tenantHeader)
      .then()
      .statusCode(200)
      .extract()
      .as(GroupFundFiscalYearCollection.class);

    int expectedCount = allRecords.getGroupFundFiscalYears().size();
    assertThat(result.getGroupFundFiscalYears().size(), equalTo(expectedCount));
    assertThat(result.getTotalRecords(), equalTo(expectedCount));

    purge(tenantHeader);
    deleteTenant(tenantJob, tenantHeader);
  }

  @Test
  public void testBatchEndpointEmptyFundIds() {
    Header tenantHeader = new Header(OKAPI_HEADER_TENANT, "gffy_batch_empty");
    TenantJob tenantJob = prepareTenant(tenantHeader, true, true);

    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(new ArrayList<>());

    postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      valueAsString(batchRequest),
      tenantHeader)
      .then()
      .statusCode(422);

    purge(tenantHeader);
    deleteTenant(tenantJob, tenantHeader);
  }

  @Test
  public void testBatchEndpointNonExistentFundIds() {
    Header tenantHeader = new Header(OKAPI_HEADER_TENANT, "gffy_batch_nonexist");
    TenantJob tenantJob = prepareTenant(tenantHeader, true, true);

    List<String> nonExistentFundIds = List.of(
      UUID.randomUUID().toString(),
      UUID.randomUUID().toString(),
      UUID.randomUUID().toString()
    );

    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(nonExistentFundIds);

    GroupFundFiscalYearCollection result = postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      valueAsString(batchRequest),
      tenantHeader)
      .then()
      .statusCode(200)
      .extract()
      .as(GroupFundFiscalYearCollection.class);

    assertThat(result.getGroupFundFiscalYears(), hasSize(0));
    assertThat(result.getTotalRecords(), equalTo(0));

    purge(tenantHeader);
    deleteTenant(tenantJob, tenantHeader);
  }

  @Test
  public void testBatchEndpointLargeNumberOfFundIds() {
    Header tenantHeader = new Header(OKAPI_HEADER_TENANT, "gffy_batch_large");
    TenantJob tenantJob = prepareTenant(tenantHeader, true, true);

    GroupFundFiscalYearCollection allRecords = getData(GROUP_FUND_FY.getEndpoint(), tenantHeader)
      .as(GroupFundFiscalYearCollection.class);

    List<String> realFundIds = allRecords.getGroupFundFiscalYears().stream()
      .map(GroupFundFiscalYear::getFundId)
      .distinct()
      .toList();

    List<String> largeFundIdList = new ArrayList<>(realFundIds);
    for (int i = realFundIds.size(); i < 100; i++) {
      largeFundIdList.add(UUID.randomUUID().toString());
    }

    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(largeFundIdList);

    GroupFundFiscalYearCollection result = postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      valueAsString(batchRequest),
      tenantHeader)
      .then()
      .statusCode(200)
      .extract()
      .as(GroupFundFiscalYearCollection.class);

    int expectedCount = allRecords.getGroupFundFiscalYears().size();
    assertThat(result.getGroupFundFiscalYears().size(), equalTo(expectedCount));
    assertThat(result.getTotalRecords(), equalTo(expectedCount));

    purge(tenantHeader);
    deleteTenant(tenantJob, tenantHeader);
  }
}
