package org.folio.rest.impl;

import static org.folio.rest.impl.TestBase.TENANT_HEADER;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.client.test.HttpClientMock2;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


@RunWith(JUnitPlatform.class)
public class StorageTestSuite {
  private static final Logger logger = LoggerFactory.getLogger(StorageTestSuite.class);

  private static Vertx vertx;
  private static int port = NetworkUtils.nextFreePort();
  public static final Header URL_TO_HEADER = new Header("X-Okapi-Url-to", "http://localhost:" + port);

  private StorageTestSuite() {
  }

  public static URL storageUrl(String path) throws MalformedURLException {
    return new URL("http", "localhost", port, path);
  }

  public static Vertx getVertx() {
    return vertx;
  }

  @BeforeAll
  public static void before() throws IOException, InterruptedException, ExecutionException, TimeoutException {

    // tests expect English error messages only, no Danish/German/...
    Locale.setDefault(Locale.US);

    vertx = Vertx.vertx();

    logger.info("Start embedded database");
    PostgresClient.setIsEmbedded(true);
    PostgresClient.getInstance(vertx).startEmbeddedPostgres();

    DeploymentOptions options = new DeploymentOptions();

    options.setConfig(new JsonObject().put("http.port", port).put(HttpClientMock2.MOCK_MODE, "true"));
    options.setWorker(true);

    startVerticle(options);

    prepareTenant(TENANT_HEADER, false, false);
  }

  @AfterAll
  public static void after() throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {
    logger.info("Delete tenant");
    deleteTenant(TENANT_HEADER);

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
    PostgresClient.stopEmbeddedPostgres();
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

    deploymentComplete.get(60, TimeUnit.SECONDS);
  }

  @Nested
  class EntitiesCrudTestNested extends EntitiesCrudTest {
  }

  @Nested
  class TenantSampleDataTestNested extends TenantSampleDataTest {
  }

  @Nested
  class LedgerFYTestNested extends LedgerFYTest {
  }

  @Nested
  class GroupFundFYTestNested extends GroupFundFYTest {
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
  class GroupBudgetTestNested extends GroupBudgetTest {
  }

  @Nested
  class HelperUtilsTestNested extends HelperUtilsTest {
  }

  @Nested
  class TransactionsSummariesTestNested extends TransactionsSummariesTest {
  }
}
