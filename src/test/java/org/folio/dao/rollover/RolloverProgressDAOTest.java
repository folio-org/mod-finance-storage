package org.folio.dao.rollover;

import static org.folio.dao.rollover.RolloverErrorDAO.LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE;
import static org.folio.rest.impl.LedgerRolloverProgressAPI.LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS_TABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverProgress;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBClient;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.SQLConnection;
import org.folio.rest.persist.interfaces.Results;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

@ExtendWith(VertxExtension.class)
public class RolloverProgressDAOTest {

  @InjectMocks
  private RolloverErrorDAO rolloverErrorDAO;

  @Mock
  private DBClient dbClient;

  @Mock
  private PostgresClient postgresClient;

  @Mock
  private Criterion criterion;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldCompletedSuccessfullyWhenRetrieveRolloverErrors(VertxTestContext testContext) {
    String id = UUID.randomUUID()
      .toString();

    LedgerFiscalYearRolloverError error = new LedgerFiscalYearRolloverError().withId(id);
    when(dbClient.getPgClient()).thenReturn(postgresClient);

    doAnswer((Answer<Void>) invocation -> {
      Handler<AsyncResult<Results<LedgerFiscalYearRolloverError>>> handler = invocation.getArgument(4);
      Results<LedgerFiscalYearRolloverError> results = new Results<>();
      results.setResults(Collections.singletonList(error));
      handler.handle(Future.succeededFuture(results));
      return null;
    }).when(postgresClient)
      .get(eq(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE), eq(LedgerFiscalYearRolloverError.class), eq(criterion), anyBoolean(), any(Handler.class));

    testContext.assertComplete(rolloverErrorDAO.get(criterion, dbClient))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertThat(event.result(), hasSize(1));
          assertEquals(error, event.result().get(0));
        });

        testContext.completeNow();
      });
  }

}
