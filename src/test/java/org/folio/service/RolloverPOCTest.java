package org.folio.service;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.utils.TenantApiTestUtil.deleteTenant;
import static org.folio.rest.utils.TenantApiTestUtil.prepareTenant;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.MalformedURLException;
import java.util.UUID;

import io.restassured.http.Header;
import org.folio.rest.impl.TestBase;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class RolloverPOCTest extends TestBase {

    private static final String ROLLOVER_TENANT = "rollover_test_tenant";
    private static final Header ROLLOVER_TENANT_HEADER = new Header(OKAPI_HEADER_TENANT, ROLLOVER_TENANT);

    @BeforeEach
    void prepareData() throws MalformedURLException {
        prepareTenant(ROLLOVER_TENANT_HEADER, true, true);
    }

    @AfterEach
    void deleteData() throws MalformedURLException {
        deleteTenant(ROLLOVER_TENANT_HEADER);
    }

    @Test
    void testRollover(Vertx vertx, VertxTestContext testContext) {
        final DBClient client = new DBClient(vertx, ROLLOVER_TENANT);
        createRolloverRecord(vertx)
            .compose(aVoid ->
                testContext.assertComplete(new RolloverPOC().runFunction("133a7916-f05e-4df4-8f7f-09eb2a7076d1", client))
                    .onComplete(event -> {
                        testContext.verify(() -> {
                            assertTrue(event.succeeded());
                        });
                        testContext.completeNow();
                    }));

    }

    private Future<Void> createRolloverRecord(Vertx vertx) {
        Promise<Void> promise = Promise.promise();
        String rollover = getFile("data/rollover/ledger_fiscal_year_rollover.json");
        String sql = String.format("INSERT INTO %s_%s.rollover (id, jsonb) VALUES ('%s', '%s');",
                ROLLOVER_TENANT, "mod_finance_storage", UUID.randomUUID().toString(), rollover);

        PostgresClient pgClient = PostgresClient.getInstance(vertx, ROLLOVER_TENANT);
        pgClient.execute(sql,  event -> {
            promise.complete();
        });
        return promise.future();
    }


}