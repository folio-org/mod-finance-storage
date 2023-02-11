package org.folio.rest.utils;

import static org.folio.rest.impl.FiscalYearAPI.FISCAL_YEAR_TABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Tuple;
import java.util.UUID;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.persist.DBClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class DBClientTest extends TestBase {

  @Test
  void save(Vertx vertx, VertxTestContext vtc) {
    String id1 = UUID.randomUUID().toString();
    String id2 = UUID.randomUUID().toString();
    String code1 = "FY" + UUID.randomUUID().toString();
    String code2 = "FY" + UUID.randomUUID().toString();
    String code3 = "FY" + UUID.randomUUID().toString();
    var dbClient = new DBClient(vertx, TENANT_NAME);
    dbClient.save(FISCAL_YEAR_TABLE, id1, new FiscalYear().withCode(code1))
    .compose(x -> dbClient.save(FISCAL_YEAR_TABLE, id2, new FiscalYear().withCode(code2)))
    .onFailure(vtc::failNow)
    .compose(x -> dbClient.save(FISCAL_YEAR_TABLE, id1, new FiscalYear().withCode(code3)))
    .onComplete(vtc.failing(e -> {
      assertHttpException(e, "duplicate key", id1);
      vtc.completeNow();
    }));
  }

  @Test
  void withTrans(Vertx vertx, VertxTestContext vtc) {
    String id1 = UUID.randomUUID().toString();
    String id2 = UUID.randomUUID().toString();
    String code1 = "fy" + UUID.randomUUID().toString();
    String code2 = "fy" + UUID.randomUUID().toString();
    var fy1 = new JsonObject().put("code", code1);
    var fy2 = new JsonObject().put("code", code2);
    var dbClient = new DBClient(vertx, TENANT_NAME);
    dbClient.withTrans(conn -> {
      return conn.execute("INSERT INTO " + FISCAL_YEAR_TABLE + " VALUES ($1, $2)", Tuple.of(id1, fy1))
          .compose(x -> conn.execute("INSERT INTO " + FISCAL_YEAR_TABLE + " VALUES ($1, $2)", Tuple.of(id2, fy2)))
          .onFailure(vtc::failNow)
          .compose(x -> conn.update(FISCAL_YEAR_TABLE, new FiscalYear().withCode(code2).withVersion(1), id1))
          .onComplete(vtc.failing(e -> assertHttpException(e, "duplicate key", code2)));
    })
    .otherwiseEmpty()
    // check that the failed future caused withTrans to execute ROLLBACK that removed id1-code1
    .compose(x -> dbClient.save(FISCAL_YEAR_TABLE, id1, new FiscalYear().withCode(code1)))
    .onComplete(vtc.succeedingThenComplete());
  }

  private static void assertHttpException(Throwable e, String expectedSubstring1, String expectedSubstring2) {
    assertThat(e, instanceOf(HttpException.class));
    assertThat(((HttpException)e).getPayload(), containsString(expectedSubstring1));
    assertThat(((HttpException)e).getPayload(), containsString(expectedSubstring2));
  }
}
