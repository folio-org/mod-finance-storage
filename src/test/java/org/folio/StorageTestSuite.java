package org.folio;

import static org.folio.rest.impl.TestBase.TENANT_HEADER;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.SecureStoreConfigurationTest;
import org.folio.dao.exchangerate.ExchangeRateSourceDAOTest;
import org.folio.dao.rollover.LedgerFiscalYearRolloverDAOTest;
import org.folio.dao.rollover.RolloverErrorDAOTest;
import org.folio.dao.rollover.RolloverProgressDAOTest;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.core.RestClientTest;
import org.folio.rest.impl.BudgetTest;
import org.folio.rest.impl.EntitiesCrudTest;
import org.folio.rest.impl.ExchangeRateSourceTest;
import org.folio.rest.impl.FinanceDataApiTest;
import org.folio.rest.impl.GroupBudgetTest;
import org.folio.rest.impl.GroupFundFYTest;
import org.folio.rest.impl.GroupTest;
import org.folio.rest.impl.HelperUtilsTest;
import org.folio.rest.impl.JobNumberTest;
import org.folio.rest.impl.LedgerFundBudgetStatusTest;
import org.folio.rest.impl.LedgerRolloverBudgetTest;
import org.folio.rest.impl.TenantSampleDataTest;
import org.folio.rest.impl.TransactionTest;
import org.folio.rest.impl.TransactionTotalApiTest;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.utils.DBClientTest;
import org.folio.service.budget.BudgetServiceTest;
import org.folio.service.email.EmailServiceTest;
import org.folio.service.exchangerate.ExchangeRateSourceServiceTest;
import org.folio.service.financedata.FinanceDataServiceTest;
import org.folio.service.group.GroupServiceTest;
import org.folio.service.rollover.LedgerRolloverServiceTest;
import org.folio.service.rollover.RolloverProgressServiceTest;
import org.folio.service.rollover.RolloverValidationServiceTest;
import org.folio.service.transactions.AllocationTransferTest;
import org.folio.service.transactions.EncumbranceTest;
import org.folio.service.transactions.PaymentCreditTest;
import org.folio.service.transactions.PendingPaymentTest;
import org.folio.utils.CalculationUtilsTest;
import org.folio.utils.SecureStoreUtilsTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;

import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class StorageTestSuite {
  private static final Logger logger = LogManager.getLogger(StorageTestSuite.class);

  private static Vertx vertx;
  private static final int port = NetworkUtils.nextFreePort();
  public static final Header URL_TO_HEADER = new Header("X-Okapi-Url-to", "http://localhost:" + port);
  private static TenantJob tenantJob;

  private StorageTestSuite() {
  }

  public static URL storageUrl(String path) {
    try {
      return new URL("http", "localhost", port, path);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(path, e);
    }
  }

  public static Vertx getVertx() {
    return vertx;
  }

  @BeforeAll
  public static void before() throws InterruptedException, ExecutionException, TimeoutException {
    // tests expect English error messages only, no Danish/German/...
    Locale.setDefault(Locale.US);

    vertx = Vertx.vertx();

    logger.info("Start container database");

    PostgresClient.setPostgresTester(new PostgresTesterContainer());

    DeploymentOptions options = new DeploymentOptions();

    options.setConfig(new JsonObject().put("http.port", port));

    startVerticle(options);

    tenantJob = prepareTenant(TENANT_HEADER, false, false);
  }

  @AfterAll
  public static void after() throws InterruptedException, ExecutionException, TimeoutException {
    logger.info("Delete tenant");
    deleteTenant(tenantJob, TENANT_HEADER);

    CompletableFuture<String> undeploymentComplete = new CompletableFuture<>();

    vertx.close(res -> {
      if (res.succeeded()) {
        undeploymentComplete.complete(null);
      } else {
        undeploymentComplete.completeExceptionally(res.cause());
      }
    });

    undeploymentComplete.get(20, TimeUnit.SECONDS);
    logger.info("Stop database");
    PostgresClient.stopPostgresTester();
  }

  private static void startVerticle(DeploymentOptions options)
    throws InterruptedException, ExecutionException, TimeoutException {

    logger.info("Start verticle");

    CompletableFuture<String> deploymentComplete = new CompletableFuture<>();

    vertx.deployVerticle(RestVerticle.class.getName(), options, res -> {
      if (res.succeeded()) {
        deploymentComplete.complete(res.result());
      } else {
        deploymentComplete.completeExceptionally(res.cause());
      }
    });

    deploymentComplete.get(360, TimeUnit.SECONDS);
  }

  @Nested
  class DBClientTestNested extends DBClientTest {
  }

  @Nested
  class EntitiesCrudTestNested extends EntitiesCrudTest {
  }

  @Nested
  class TenantSampleDataTestNested extends TenantSampleDataTest {
  }

  @Nested
  class GroupFundFYTestNested extends GroupFundFYTest {
  }

  @Nested
  class GroupServiceTestNested extends GroupServiceTest {
  }

  @Nested
  class GroupTestNested extends GroupTest {
  }

  @Nested
  class BudgetServiceTestNested extends BudgetServiceTest {
  }

  @Nested
  class BudgetTestNested extends BudgetTest {
  }

  @Nested
  class TransactionTestNested extends TransactionTest {
  }

  @Nested
  class LedgerFundBudgetStatusTestNested extends LedgerFundBudgetStatusTest {
  }

  @Nested
  class LedgerRolloverBudgetTestNested extends LedgerRolloverBudgetTest {
  }

  @Nested
  class GroupBudgetTestNested extends GroupBudgetTest {
  }

  @Nested
  class HelperUtilsTestNested extends HelperUtilsTest {
  }

  @Nested
  class LedgerRolloverServiceTestNested extends LedgerRolloverServiceTest {}

  @Nested
  class LedgerFiscalYearRolloverDAOTestNested extends LedgerFiscalYearRolloverDAOTest {}

  @Nested
  class RolloverProgressDAOTestNested extends RolloverProgressDAOTest {}

  @Nested
  class RestClientTestNested extends RestClientTest {}

  @Nested
  class RolloverProgressServiceTestNested extends RolloverProgressServiceTest {}

  @Nested
  class RolloverErrorDAOTestNested extends RolloverErrorDAOTest {}

  @Nested
  class CalculationUtilsTestNested extends CalculationUtilsTest {}

  @Nested
  class RolloverValidationServiceTestNested extends RolloverValidationServiceTest {}

  @Nested
  class EmailServiceTestNested extends EmailServiceTest {}

  @Nested
  class AllocationTransferTestNested extends AllocationTransferTest {}

  @Nested
  class EncumbranceTestNested extends EncumbranceTest {}

  @Nested
  class PaymentCreditTestNested extends PaymentCreditTest {}

  @Nested
  class PendingPaymentTestNested extends PendingPaymentTest {}

  @Nested
  class FinanceDataApiTestNested extends FinanceDataApiTest {}

  @Nested
  class FinanceDataServiceTestNested extends FinanceDataServiceTest {}

  @Nested
  class TransactionTotalApiTestNested extends TransactionTotalApiTest {}

  @Nested
  class JobNumberTestNested extends JobNumberTest {}

  @Nested
  class ExchangeRateSourceTestNested extends ExchangeRateSourceTest {}

  @Nested
  class ExchangeRateSourceDAOTestNested extends ExchangeRateSourceDAOTest {}

  @Nested
  class ExchangeRateSourceServiceTestNested extends ExchangeRateSourceServiceTest {}

  @Nested
  class SecureStoreConfigurationTestNested extends SecureStoreConfigurationTest {}

  @Nested
  class SecureStoreUtilsTestNested extends SecureStoreUtilsTest {}
}
