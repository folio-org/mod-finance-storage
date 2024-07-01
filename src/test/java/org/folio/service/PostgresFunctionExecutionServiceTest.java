package org.folio.service;

import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.rest.impl.TestBase;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRollover;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.DBConn;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(VertxExtension.class)
class PostgresFunctionExecutionServiceTest extends TestBase {

  PostgresFunctionExecutionService service = new PostgresFunctionExecutionService();

  static Stream<String> rolloverSqlInjection() {
    return Stream.of("foo", "foo'bar", "foo\"bar", "foo$$bar");
  }

  @ParameterizedTest
  @MethodSource
  void rolloverSqlInjection(String username, Vertx vertx, VertxTestContext vtc) {
    var rollover = new LedgerFiscalYearRollover()
        .withId(UUID.randomUUID().toString())
        .withMetadata(new Metadata().withCreatedByUsername(username));
    withConn(vertx, conn -> service.runBudgetEncumbrancesRolloverScript(rollover, conn))
    .onComplete(vtc.succeedingThenComplete());
  }

  <T> Future<T> withConn(Vertx vertx, Function<DBConn, Future<T>> function) {
    return new DBClient(vertx, TENANT_NAME).withConn(function);
  }

}
