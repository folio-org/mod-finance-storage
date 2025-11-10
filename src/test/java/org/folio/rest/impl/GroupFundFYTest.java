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
import io.vertx.core.json.JsonObject;

public class GroupFundFYTest extends TestBase {

  private static final Header GROUP_FUND_FY_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, "groupfundfy");

  @Test
  public void testGetQuery() {
    TenantJob tenantJob = prepareTenant(GROUP_FUND_FY_TENANT_HEADER, true, true);

    // search for GET
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint(), GROUP_FUND_FY.getInitialQuantity(), GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "groups"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==History", GROUP_FUND_FY.getInitialQuantity(), GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "FY"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fiscalYear.code==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fiscalYear.code==FY2025", 12, GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "fund"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.code==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.fundStatus==Active", 12, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fund.name=European", 2, GROUP_FUND_FY_TENANT_HEADER);

    // search by field from "fund type"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fundType.name==NonExistent", 0, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=fundType.name==Approvals", 2, GROUP_FUND_FY_TENANT_HEADER);

    // search with fields from "ledgers"
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=ledger.name==One-time", 12, GROUP_FUND_FY_TENANT_HEADER);

    // search by fields from different tables
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.name==History and fiscalYear.code==FY2025", 12, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.code==HIST and fiscalYear.periodEnd < 2026-01-01 and fund.fundStatus==Inactive and ledger.name==One-time", 1, GROUP_FUND_FY_TENANT_HEADER);
    verifyCollectionQuantity(GROUP_FUND_FY.getEndpoint() + "?query=group.code==HIST and fundType.name==Approvals and ledger.name==One-time", 2, GROUP_FUND_FY_TENANT_HEADER);
    // search with invalid cql query
    testInvalidCQLQuery(GROUP_FUND_FY.getEndpoint() + "?query=invalid-query");

    purge(GROUP_FUND_FY_TENANT_HEADER);
    deleteTenant(tenantJob, GROUP_FUND_FY_TENANT_HEADER);
  }

  @Test
  public void testBatchEndpointSuccess() {
    // Prepare tenant with sample data
    TenantJob tenantJob = prepareTenant(GROUP_FUND_FY_TENANT_HEADER, true, true);

    // Get all group-fund-fiscal-year records to extract fundIds
    GroupFundFiscalYearCollection allRecords = getData(GROUP_FUND_FY.getEndpoint(), GROUP_FUND_FY_TENANT_HEADER)
      .as(GroupFundFiscalYearCollection.class);

    // Extract 3 unique fundIds from the sample data
    List<String> fundIds = allRecords.getGroupFundFiscalYears().stream()
      .map(GroupFundFiscalYear::getFundId)
      .distinct()
      .limit(3)
      .toList();

    // Create batch request
    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(fundIds);

    // Call batch endpoint
    GroupFundFiscalYearCollection result = postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      JsonObject.mapFrom(batchRequest).encodePrettily(),
      GROUP_FUND_FY_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract()
      .as(GroupFundFiscalYearCollection.class);

    // Verify results - count how many records actually match these fundIds
    long expectedCount = allRecords.getGroupFundFiscalYears().stream()
      .filter(gffy -> fundIds.contains(gffy.getFundId()))
      .count();

    assertThat(result.getGroupFundFiscalYears(), hasSize((int) expectedCount));
    assertThat(result.getTotalRecords(), equalTo((int) expectedCount));

    // Verify all returned records have fundIds from the request
    result.getGroupFundFiscalYears().forEach(gffy -> {
      assertThat("FundId should be in the requested list", fundIds.contains(gffy.getFundId()));
    });

    purge(GROUP_FUND_FY_TENANT_HEADER);
    deleteTenant(tenantJob, GROUP_FUND_FY_TENANT_HEADER);
  }

  @Test
  public void testBatchEndpointWithFilters() {
    // Prepare tenant with sample data
    TenantJob tenantJob = prepareTenant(GROUP_FUND_FY_TENANT_HEADER, true, true);

    // Get all records
    GroupFundFiscalYearCollection allRecords = getData(GROUP_FUND_FY.getEndpoint(), GROUP_FUND_FY_TENANT_HEADER)
      .as(GroupFundFiscalYearCollection.class);

    // Get a specific record to use for filtering
    GroupFundFiscalYear sampleRecord = allRecords.getGroupFundFiscalYears().get(0);
    List<String> fundIds = List.of(sampleRecord.getFundId());

    // Create batch request with filters
    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(fundIds)
      .withFiscalYearId(sampleRecord.getFiscalYearId())
      .withGroupId(sampleRecord.getGroupId());

    // Call batch endpoint
    GroupFundFiscalYearCollection result = postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      JsonObject.mapFrom(batchRequest).encodePrettily(),
      GROUP_FUND_FY_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract()
      .as(GroupFundFiscalYearCollection.class);

    // Verify results - should return exactly 1 record matching all filters
    assertThat(result.getGroupFundFiscalYears(), hasSize(1));
    assertThat(result.getTotalRecords(), equalTo(1));

    GroupFundFiscalYear resultRecord = result.getGroupFundFiscalYears().get(0);
    assertThat(resultRecord.getFundId(), equalTo(sampleRecord.getFundId()));
    assertThat(resultRecord.getFiscalYearId(), equalTo(sampleRecord.getFiscalYearId()));
    assertThat(resultRecord.getGroupId(), equalTo(sampleRecord.getGroupId()));

    purge(GROUP_FUND_FY_TENANT_HEADER);
    deleteTenant(tenantJob, GROUP_FUND_FY_TENANT_HEADER);
  }

  @Test
  public void testBatchEndpointWithMultipleFunds() {
    // Prepare tenant with sample data
    TenantJob tenantJob = prepareTenant(GROUP_FUND_FY_TENANT_HEADER, true, true);

    // Get all records to extract multiple unique fundIds
    GroupFundFiscalYearCollection allRecords = getData(GROUP_FUND_FY.getEndpoint(), GROUP_FUND_FY_TENANT_HEADER)
      .as(GroupFundFiscalYearCollection.class);

    // Get all unique fundIds
    List<String> allFundIds = allRecords.getGroupFundFiscalYears().stream()
      .map(GroupFundFiscalYear::getFundId)
      .distinct()
      .toList();

    // Create batch request with all fundIds
    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(allFundIds);

    // Call batch endpoint
    GroupFundFiscalYearCollection result = postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      JsonObject.mapFrom(batchRequest).encodePrettily(),
      GROUP_FUND_FY_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract()
      .as(GroupFundFiscalYearCollection.class);

    // Verify all records are returned - should match the original count
    int expectedCount = allRecords.getGroupFundFiscalYears().size();
    assertThat(result.getGroupFundFiscalYears(), hasSize(expectedCount));
    assertThat(result.getTotalRecords(), equalTo(expectedCount));

    purge(GROUP_FUND_FY_TENANT_HEADER);
    deleteTenant(tenantJob, GROUP_FUND_FY_TENANT_HEADER);
  }

  @Test
  public void testBatchEndpointEmptyFundIds() {
    // Prepare tenant with sample data
    TenantJob tenantJob = prepareTenant(GROUP_FUND_FY_TENANT_HEADER, true, true);

    // Create batch request with empty fundIds
    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(new ArrayList<>());

    // Call batch endpoint - should return 422 Unprocessable Entity due to schema validation
    postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      JsonObject.mapFrom(batchRequest).encodePrettily(),
      GROUP_FUND_FY_TENANT_HEADER)
      .then()
      .statusCode(422);  // Empty array violates minItems: 1 in schema

    purge(GROUP_FUND_FY_TENANT_HEADER);
    deleteTenant(tenantJob, GROUP_FUND_FY_TENANT_HEADER);
  }

  @Test
  public void testBatchEndpointNonExistentFundIds() {
    // Prepare tenant with sample data
    TenantJob tenantJob = prepareTenant(GROUP_FUND_FY_TENANT_HEADER, true, true);

    // Create batch request with non-existent fundIds
    List<String> nonExistentFundIds = List.of(
      UUID.randomUUID().toString(),
      UUID.randomUUID().toString(),
      UUID.randomUUID().toString()
    );

    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(nonExistentFundIds);

    // Call batch endpoint
    GroupFundFiscalYearCollection result = postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      JsonObject.mapFrom(batchRequest).encodePrettily(),
      GROUP_FUND_FY_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract()
      .as(GroupFundFiscalYearCollection.class);

    // Verify empty result for non-existent fundIds
    assertThat(result.getGroupFundFiscalYears(), hasSize(0));
    assertThat(result.getTotalRecords(), equalTo(0));

    purge(GROUP_FUND_FY_TENANT_HEADER);
    deleteTenant(tenantJob, GROUP_FUND_FY_TENANT_HEADER);
  }

  @Test
  public void testBatchEndpointLargeNumberOfFundIds() {
    // Prepare tenant with sample data
    TenantJob tenantJob = prepareTenant(GROUP_FUND_FY_TENANT_HEADER, true, true);

    // Create a large list of fundIds (simulate 100 fundIds)
    // Mix real fundIds with non-existent ones
    GroupFundFiscalYearCollection allRecords = getData(GROUP_FUND_FY.getEndpoint(), GROUP_FUND_FY_TENANT_HEADER)
      .as(GroupFundFiscalYearCollection.class);

    List<String> realFundIds = allRecords.getGroupFundFiscalYears().stream()
      .map(GroupFundFiscalYear::getFundId)
      .distinct()
      .toList();

    // Create 100 fundIds: real ones + random ones
    List<String> largeFundIdList = new ArrayList<>(realFundIds);
    for (int i = realFundIds.size(); i < 100; i++) {
      largeFundIdList.add(UUID.randomUUID().toString());
    }

    GroupFundFiscalYearBatchRequest batchRequest = new GroupFundFiscalYearBatchRequest()
      .withFundIds(largeFundIdList);

    // Call batch endpoint - should handle large array efficiently
    GroupFundFiscalYearCollection result = postData(GROUP_FUND_FY.getEndpoint() + "/batch",
      JsonObject.mapFrom(batchRequest).encodePrettily(),
      GROUP_FUND_FY_TENANT_HEADER)
      .then()
      .statusCode(200)
      .extract()
      .as(GroupFundFiscalYearCollection.class);

    // Verify only real records are returned - should match the original count since we queried all real fundIds
    int expectedCount = allRecords.getGroupFundFiscalYears().size();
    assertThat(result.getGroupFundFiscalYears(), hasSize(expectedCount));
    assertThat(result.getTotalRecords(), equalTo(expectedCount));

    purge(GROUP_FUND_FY_TENANT_HEADER);
    deleteTenant(tenantJob, GROUP_FUND_FY_TENANT_HEADER);
  }

}
