package org.folio.rest.impl;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.purge;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;

import org.folio.rest.jaxrs.model.TenantJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.http.Header;

public class TransactionTest extends TestBase {

  protected static final String TRANSACTION_TEST_TENANT = "transactiontesttenant";
  protected static final Header TRANSACTION_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, TRANSACTION_TEST_TENANT);

  private static final String BATCH_TRANSACTION_SAMPLE = "data/transactions/batch/batch_with_patch.json";
  private static final String BATCH_TRANSACTION_ENDPOINT = "/finance-storage/transactions/batch-all-or-nothing";
  private static TenantJob tenantJob;

  @BeforeEach
  void prepareData() {
    tenantJob = prepareTenant(TRANSACTION_TENANT_HEADER, false, true);
  }

  @AfterEach
  void deleteData() {
    purge(TRANSACTION_TENANT_HEADER);
  }

  @AfterAll
  public static void after() {
    deleteTenant(tenantJob, TRANSACTION_TENANT_HEADER);
  }

  @Test
  void testBatchTransactionsPatch() {
    String batchAsString = getFile(BATCH_TRANSACTION_SAMPLE);
    postData(BATCH_TRANSACTION_ENDPOINT, batchAsString, TRANSACTION_TENANT_HEADER).then()
      .statusCode(500);
  }

}
