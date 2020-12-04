package org.folio.service;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.util.ResponseUtils.handleFailure;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.restassured.http.Header;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Tuple;

@ExtendWith(VertxExtension.class)
class RolloverPOCTest extends TestBase {

  private static final String ROLLOVER_TENANT = "rollover_test_tenant";
  private static final Header ROLLOVER_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, ROLLOVER_TENANT);

  private static Map<String, Budget> expectedBudgets = new HashMap<>();
  private static Map<String, Transaction> expectedAllocations = new HashMap<>();
  private static Map<String, Transaction> expectedEncumbrances = new HashMap<>();
  private static Transaction expectedTransfer = new Transaction().withAmount(40d)
          .withTransactionType(Transaction.TransactionType.TRANSFER)
          .withToFundId("a9e551fd-10b2-4e23-9cc9-a7afa0efc27b")
          .withFiscalYearId("184b5dc5-92f6-4db7-b996-b549d88f5e4a")
          .withCurrency("USD")
          .withSource(Transaction.Source.USER);

  static {
    expectedBudgets.put("cc898a51-fa96-45ea-ae03-d8e08ae46222", defaultBudget().withAllocated(160d)
      .withAvailable(160d)
      .withAllowableEncumbrance(110d)
      .withName("GIFT-FY2021")
      .withFundId("cc898a51-fa96-45ea-ae03-d8e08ae46222"));

    expectedBudgets.put("d5c3e0e8-4784-48f3-ad35-64ff32c64317", defaultBudget().withAllocated(0d)
      .withAvailable(0d)
      .withName("HIST-test-FY2021")
      .withFundId("d5c3e0e8-4784-48f3-ad35-64ff32c64317"));

    expectedBudgets.put("3669af88-8e10-40b7-a7a7-4ed40e7c4175", defaultBudget().withAllocated(77d)
      .withAvailable(77d)
      .withName("LATIN-FY2021")
      .withFundId("3669af88-8e10-40b7-a7a7-4ed40e7c4175"));

    expectedBudgets.put("5c5c050d-9add-48f1-a8dd-866211969f43", defaultBudget().withAllocated(88d)
      .withAvailable(56.5)
      .withEncumbered(31.5)
      .withUnavailable(31.5)
      .withName("LAW-FY2021")
      .withFundId("5c5c050d-9add-48f1-a8dd-866211969f43"));

    expectedBudgets.put("a9e551fd-10b2-4e23-9cc9-a7afa0efc27b", defaultBudget().withAllocated(110d)
      .withAvailable(150d)
      .withNetTransfers(40d)
      .withAllowableEncumbrance(110d)
      .withName("SCIENCE-FY2021")
      .withFundId("a9e551fd-10b2-4e23-9cc9-a7afa0efc27b"));

    expectedAllocations.put("3669af88-8e10-40b7-a7a7-4ed40e7c4175", defaultAllocation()
                                                                      .withAmount(77d)
                                                                      .withToFundId("3669af88-8e10-40b7-a7a7-4ed40e7c4175")
                                                                      );
    expectedAllocations.put("5c5c050d-9add-48f1-a8dd-866211969f43", defaultAllocation()
                                                                      .withAmount(88d)
                                                                      .withToFundId("5c5c050d-9add-48f1-a8dd-866211969f43"));
    expectedAllocations.put("a9e551fd-10b2-4e23-9cc9-a7afa0efc27b", defaultAllocation()
                                                                      .withAmount(110d)
                                                                      .withToFundId("a9e551fd-10b2-4e23-9cc9-a7afa0efc27b"));
    expectedAllocations.put("cc898a51-fa96-45ea-ae03-d8e08ae46222", defaultAllocation()
                                                                      .withAmount(160d)
                                                                      .withToFundId("cc898a51-fa96-45ea-ae03-d8e08ae46222"));

    expectedEncumbrances.put("2f3545c5-433e-4785-8025-ddd1733d7748", defaultEncumbrance()
            .withAmount(27.5)
            .withFromFundId("5c5c050d-9add-48f1-a8dd-866211969f43")
            .withDescription("Expended is lower")
            .withEncumbrance(new Encumbrance()
                    .withAmountExpended(0d)
                    .withAmountAwaitingPayment(0d)
                    .withStatus(Encumbrance.Status.UNRELEASED)
                    .withInitialAmountEncumbered(27.5)
                    .withSourcePurchaseOrderId("2f3545c5-433e-4785-8025-ddd1733d7748")
                    .withSourcePoLineId("2f3545c5-433e-4785-8025-ddd1733d7747")
                    .withReEncumber(true)
                    .withOrderType(Encumbrance.OrderType.ONGOING)
                    .withSubscription(true)
            )
          );
    expectedEncumbrances.put("12958add-fb00-49bc-b0f2-8962f91c84af", defaultEncumbrance()
            .withAmount(4d)
            .withFromFundId("5c5c050d-9add-48f1-a8dd-866211969f43")
            .withDescription("Encumber Remaining")
            .withEncumbrance(new Encumbrance()
                    .withAmountExpended(0d)
                    .withAmountAwaitingPayment(0d)
                    .withStatus(Encumbrance.Status.UNRELEASED)
                    .withInitialAmountEncumbered(4d)
                    .withSourcePurchaseOrderId("12958add-fb00-49bc-b0f2-8962f91c84af")
                    .withSourcePoLineId("12958add-fb00-49bc-b0f2-8962f91c84ae")
                    .withReEncumber(true)
                    .withOrderType(Encumbrance.OrderType.ONE_TIME)
                    .withSubscription(false)
            )
    );
  }

  private static Transaction defaultEncumbrance() {
    return new Transaction()
            .withFiscalYearId("184b5dc5-92f6-4db7-b996-b549d88f5e4a")
            .withTransactionType(Transaction.TransactionType.ENCUMBRANCE)
            .withCurrency("USD")
            .withSource(Transaction.Source.PO_LINE);
  }

  private static Transaction defaultAllocation() {
    return new Transaction()
            .withFiscalYearId("184b5dc5-92f6-4db7-b996-b549d88f5e4a")
            .withSource(Transaction.Source.USER)
            .withCurrency("USD")
            .withTransactionType(Transaction.TransactionType.ALLOCATION);
  }

  private static Budget defaultBudget() {
    return new Budget().withAwaitingPayment(0d)
      .withExpenditures(0d)
      .withFiscalYearId("184b5dc5-92f6-4db7-b996-b549d88f5e4a")
      .withOverEncumbrance(0d)
      .withOverExpended(0d)
      .withAllowableExpenditure(100d)
      .withAllowableEncumbrance(100d)
      .withBudgetStatus(Budget.BudgetStatus.ACTIVE)
      .withUnavailable(0d)
      .withNetTransfers(0d)
      .withEncumbered(0d);
  }

  @BeforeEach
  void prepareData(Vertx vertx, VertxTestContext testContext)
      throws MalformedURLException {
    prepareTenant(ROLLOVER_TENANT_HEADER, true, true);
    loadTestData(vertx, testContext);
  }

  private void loadTestData(Vertx vertx, VertxTestContext testContext) {

    URL ledgers = RolloverPOCTest.class.getClassLoader()
      .getResource("rollover/ledgers.data");
    URL funds = RolloverPOCTest.class.getClassLoader()
      .getResource("rollover/funds.data");
    URL budgets = RolloverPOCTest.class.getClassLoader()
      .getResource("rollover/budgets.data");
    URL encumbrances = RolloverPOCTest.class.getClassLoader()
      .getResource("rollover/encumbrances.data");
    URL rollover = RolloverPOCTest.class.getClassLoader()
      .getResource("rollover/rollover.data");
    testContext.assertComplete(copy(vertx, ledgers.getPath(), "ledger").compose(aVoid -> copy(vertx, funds.getPath(), "fund"))
      .compose(aVoid -> copy(vertx, budgets.getPath(), "budget"))
      .compose(aVoid -> copy(vertx, encumbrances.getPath(), "transaction"))
      .compose(aVoid -> copy(vertx, rollover.getPath(), "rollover"))
      .onComplete(event -> testContext.completeNow()));

  }

  private Future<Void> copy(Vertx vertx, String path, String table) {
    String copy = String.format("COPY %s_mod_finance_storage.%s (id, jsonb) FROM '%s' ENCODING 'UTF8' DELIMITER E'\t';",
        ROLLOVER_TENANT, table, path.substring(1));
    Promise<Void> promise = Promise.promise();
    PostgresClient.getInstance(vertx)
      .execute(copy, event -> {
        if (event.succeeded()) {
          promise.complete();
        } else {
          promise.fail(event.cause());
        }
      });
    return promise.future();
  }

  @AfterEach
  void deleteData() throws MalformedURLException {
    deleteTenant(ROLLOVER_TENANT_HEADER);
  }

  @Test
  void testRollover(Vertx vertx, VertxTestContext testContext) {
    final DBClient client = new DBClient(vertx, ROLLOVER_TENANT);
    testContext.assertComplete(new RolloverPOC().runFunction("1ac18928-a447-491d-94c9-875dcc7bb3e5", client))
      .compose(v -> assertBudgets(client, testContext))
      .compose(v -> assertAllocations(client, testContext))
      .compose(v -> assertTransfers(client, testContext))
      .compose(v -> assertEncumbrances(client, testContext))
      .compose(v -> assertErrors(client, testContext))
      .onComplete(event -> {
        testContext.verify(() -> assertTrue(event.succeeded()));
        testContext.completeNow();
      });

  }

  private Future<List<Budget>> getBudgets(DBClient client) {
    String sql = String
      .format("SELECT budget.jsonb FROM %1$s_mod_finance_storage.budget AS budget INNER JOIN %1$s_mod_finance_storage.fund AS fund "
          + "ON fund.id = budget.fundId " + "WHERE fund.ledgerId::text=$1 AND budget.fiscalYearId::text=$2", ROLLOVER_TENANT);
    Promise<List<Budget>> promise = Promise.promise();
    client.getPgClient()
      .select(sql, Tuple.of("1ac18928-a447-491d-94c9-875dcc7bb3e5", "184b5dc5-92f6-4db7-b996-b549d88f5e4a"), reply -> {
        if (reply.failed()) {
          handleFailure(promise, reply);
        } else {
          List<Budget> budgets = new ArrayList<>();
          reply.result()
            .spliterator()
            .forEachRemaining(row -> budgets.add(row.get(JsonObject.class, 0)
              .mapTo(Budget.class)));
          promise.complete(budgets);
        }
      });
    return promise.future();
  }

  private Future<Void> assertBudgets(DBClient client, VertxTestContext testContext) {
    return getBudgets(client).map(budgets -> {
      testContext.verify(() -> budgets.forEach(budget -> {
        Budget expectedBudget = expectedBudgets.get(budget.getFundId());
        expectedBudget.withId(budget.getId())
          .withMetadata(budget.getMetadata());
        assertEquals(expectedBudget, budget);
      }));
      return null;
    });
  }

  private Future<Void> assertAllocations(DBClient client, VertxTestContext testContext) {
    return getTransactions(client, Transaction.TransactionType.ALLOCATION)
      .map(transactions -> {
        testContext.verify(() -> transactions.forEach(allocation -> {
          Transaction expectedAllocation = expectedAllocations.get(allocation.getToFundId());
          expectedAllocation.withId(allocation.getId())
                  .withMetadata(allocation.getMetadata());
          assertEquals(expectedAllocation, allocation);
        }));
        return null;
      });
  }

  private Future<Void> assertTransfers(DBClient client, VertxTestContext testContext) {
    return getTransactions(client, Transaction.TransactionType.ROLLOVER_TRANSFER)
            .map(transactions -> {
              testContext.verify(() -> transactions.forEach(transfer -> {
                expectedTransfer.withId(transfer.getId())
                        .withMetadata(transfer.getMetadata());
                assertEquals(expectedTransfer, transfer);
              }));
              return null;
            });
  }


  private Future<Void> assertEncumbrances(DBClient client, VertxTestContext testContext) {
    return getTransactions(client, Transaction.TransactionType.ENCUMBRANCE)
            .map(transactions -> {
              testContext.verify(() -> transactions.forEach(encumbrance -> {
                Transaction expectedEncumbrance = expectedEncumbrances.get(encumbrance.getEncumbrance().getSourcePurchaseOrderId());
                expectedEncumbrance.withId(encumbrance.getId())
                        .withMetadata(encumbrance.getMetadata());
                assertEquals(expectedEncumbrance, encumbrance);
              }));
              return null;
            });
  }

  private Future<List<Transaction>> getTransactions(DBClient client, Transaction.TransactionType transactionType) {
    String sql = String
            .format("SELECT tr.jsonb FROM %1$s_mod_finance_storage.transaction AS tr INNER JOIN %1$s_mod_finance_storage.fund AS fund "
                    + "ON fund.id = tr.toFundId " + "WHERE fund.ledgerId::text=$1 AND tr.fiscalYearId::text=$2 AND tr.jsonb->>'transactionType'=$3", ROLLOVER_TENANT);
    Promise<List<Transaction>> promise = Promise.promise();
    client.getPgClient()
            .select(sql, Tuple.of("1ac18928-a447-491d-94c9-875dcc7bb3e5", "184b5dc5-92f6-4db7-b996-b549d88f5e4a", transactionType.value()), reply -> {
              if (reply.failed()) {
                handleFailure(promise, reply);
              } else {
                List<Transaction> transactions = new ArrayList<>();
                reply.result()
                        .spliterator()
                        .forEachRemaining(row -> transactions.add(row.get(JsonObject.class, 0)
                                .mapTo(Transaction.class)));
                promise.complete(transactions);
              }
            });
    return promise.future();
  }

  private Future<Void> assertErrors(DBClient client, VertxTestContext testContext) {
    return getRolloverErrors(client)
            .map(errors -> {
              testContext.verify(() -> errors.forEach(error -> {
                assertEquals("ORDER", error.getString("errorType"));
                assertEquals("Create encumbrance", error.getString("failedAction"));
                assertNotNull(error.getJsonObject("details"));
                assertEquals("ed3454cc-d42a-411a-bc22-46285c3e98d9", error.getJsonObject("details").getString("purchaseOrderId"));
                assertEquals("ed3454cc-d42a-411a-bc22-46285c3e98d8", error.getJsonObject("details").getString("poLineId"));
                assertEquals(11d, error.getJsonObject("details").getDouble("amount"));
                assertEquals("d5c3e0e8-4784-48f3-ad35-64ff32c64317", error.getJsonObject("details").getString("fundId"));
              }));
              return null;
            });
  }

  private Future<List<JsonObject>> getRolloverErrors(DBClient client) {
    String sql = String
            .format("SELECT err.jsonb FROM %1$s_mod_finance_storage.ledger_fiscal_year_rollover_errors AS err "
                    + "WHERE err.jsonb->>'ledgerId'=$1", ROLLOVER_TENANT);
    Promise<List<JsonObject>> promise = Promise.promise();
    client.getPgClient()
            .select(sql, Tuple.of("1ac18928-a447-491d-94c9-875dcc7bb3e5"), reply -> {
              if (reply.failed()) {
                handleFailure(promise, reply);
              } else {
                List<JsonObject> transactions = new ArrayList<>();
                reply.result()
                        .spliterator()
                        .forEachRemaining(row -> transactions.add(row.get(JsonObject.class, 0)));
                promise.complete(transactions);
              }
            });
    return promise.future();
  }


}