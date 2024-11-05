package org.folio.rest.impl;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.StorageTestSuite;
import org.folio.rest.jaxrs.model.FundUpdateLog;
import org.folio.rest.jaxrs.resource.FinanceStorageFundUpdateLog;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PostgresClient;
import org.folio.utils.CopilotGenerated;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.folio.rest.impl.FundUpdateLogAPI.FUND_UPDATE_LOG_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

@ExtendWith(VertxExtension.class)
@CopilotGenerated(partiallyGenerated = true)
public class FundUpdateLogTest extends TestBase {

  private static final String ID = UUID.randomUUID().toString();
  private static final String ENTITY_NAME = "FUND_UPDATE_LOG";
  private static final String FUND_UPDATE_LOG_ENDPOINT = HelperUtils.getEndpoint(FinanceStorageFundUpdateLog.class);

  @BeforeEach
  void saveFundUpdateLogData(VertxTestContext context) {
    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .saveAndReturnUpdatedEntity(FUND_UPDATE_LOG_TABLE, ID, new FundUpdateLog())
      .onComplete(context.succeedingThenComplete());
  }

  @AfterEach
  void deleteFundUpdateLogData(VertxTestContext context) {
    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .delete(FUND_UPDATE_LOG_TABLE, ID)
      .onComplete(context.succeedingThenComplete());
  }

  @Test
  void getCollection() {
    logger.info(String.format("--- mod-finance-storage %s test: Verifying only 1 log was created ... ", ENTITY_NAME));
    verifyCollectionQuantity(FUND_UPDATE_LOG_ENDPOINT, 1);
  }

  @Test
  void getById() {
    logger.info(String.format("--- mod-finance-storage %1$s test: Fetching %1$s with ID %2$s", ENTITY_NAME, ID));
    testEntitySuccessfullyFetched(FUND_UPDATE_LOG_ENDPOINT + "/{id}", String.valueOf(ID));
  }

  @Test
  void createFundUpdateLog(VertxTestContext context) {
    var jobId = UUID.randomUUID().toString();
    var jobNumber = 1;
    var jobName = "Update Fund";

    FundUpdateLog newLog = new FundUpdateLog().withId(jobId).withJobNumber(jobNumber).withJobName(jobName);
    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .saveAndReturnUpdatedEntity(FUND_UPDATE_LOG_TABLE, String.valueOf(newLog.getId()), newLog)
      .onComplete(context.succeeding(result -> {
        assertNotNull(result);
        context.completeNow();
      }));
  }

  @Test
  void updateFundUpdateLog(VertxTestContext context) {
    FundUpdateLog updatedLog = new FundUpdateLog().withId(ID).withJobName("Updated JobName");
    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .update(FUND_UPDATE_LOG_TABLE, updatedLog, ID)
      .onComplete(context.succeeding(result -> {
        assertEquals(1, result.rowCount());
        context.completeNow();
      }));
  }

  @Test
  void deleteFundUpdateLog(VertxTestContext context) {
    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .delete(FUND_UPDATE_LOG_TABLE, ID)
      .onComplete(context.succeeding(result -> {
        assertEquals(1, result.rowCount());
        context.completeNow();
      }));
  }
}
