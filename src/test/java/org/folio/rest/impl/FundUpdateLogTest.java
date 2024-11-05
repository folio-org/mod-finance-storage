package org.folio.rest.impl;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.StorageTestSuite;
import org.folio.rest.jaxrs.model.FundUpdateLog;
import org.folio.rest.jaxrs.resource.FinanceStorageFundUpdateLog;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.folio.rest.impl.FundUpdateLogAPI.FUND_UPDATE_LOG_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(VertxExtension.class)
public class FundUpdateLogTest extends TestBase {

  private static final Integer LOG_ID = 102;
  private static final String ENTITY_NAME = "FUND_UPDATE_LOG";
  private static final String FUND_UPDATE_LOG_ENDPOINT = HelperUtils.getEndpoint(FinanceStorageFundUpdateLog.class);

  @BeforeAll
  static void saveFundUpdateLogData(VertxTestContext context) {
    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .saveAndReturnUpdatedEntity(FUND_UPDATE_LOG_TABLE, String.valueOf(LOG_ID), new FundUpdateLog())
      .onComplete(context.succeedingThenComplete());
  }

  @Test
  void getCollection() {
    logger.info(String.format("--- mod-finance-storage %s test: Verifying only 1 log was created ... ", ENTITY_NAME));
    verifyCollectionQuantity(FUND_UPDATE_LOG_ENDPOINT, 1);
  }

  @Test
  void getById() {
    logger.info(String.format("--- mod-finance-storage %1$s test: Fetching %1$s with ID %2$s", ENTITY_NAME, LOG_ID));
    testEntitySuccessfullyFetched(FUND_UPDATE_LOG_ENDPOINT + "/{id}", String.valueOf(LOG_ID));
  }

  @Test
  void createFundUpdateLog(VertxTestContext context) {
    FundUpdateLog newLog = new FundUpdateLog().withId(1);
    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .saveAndReturnUpdatedEntity(FUND_UPDATE_LOG_TABLE, String.valueOf(newLog.getId()), newLog)
      .onComplete(context.succeeding(result -> {
        assertNotNull(result);
        context.completeNow();
      }));
  }

  @Test
  void updateFundUpdateLog(VertxTestContext context) {
    FundUpdateLog updatedLog = new FundUpdateLog().withId(LOG_ID).withJobName("Updated JobName");
    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .update(FUND_UPDATE_LOG_TABLE, updatedLog, String.valueOf(LOG_ID))
      .onComplete(context.succeeding(result -> {
        assertEquals(1, result.rowCount());
        context.completeNow();
      }));
  }

  @Test
  void deleteFundUpdateLog(VertxTestContext context) {
    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .delete(FUND_UPDATE_LOG_TABLE, LOG_ID)
      .onComplete(context.succeeding(result -> {
        assertEquals(1, result.rowCount());
        context.completeNow();
      }));
  }
}
