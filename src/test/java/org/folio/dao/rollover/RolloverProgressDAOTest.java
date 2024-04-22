package org.folio.dao.rollover;

import static org.folio.dao.rollover.RolloverErrorDAO.LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.util.List;
import java.util.UUID;

import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverError;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.DBConn;
import org.folio.rest.persist.interfaces.Results;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class RolloverProgressDAOTest {

  private AutoCloseable mockitoMocks;

  @InjectMocks
  private RolloverErrorDAO rolloverErrorDAO;

  @Mock
  private DBConn conn;

  @Mock
  private Criterion criterion;


  @BeforeEach
  public void initMocks() {
    mockitoMocks = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  public void afterEach() throws Exception {
    mockitoMocks.close();
  }

  @Test
  void shouldCompletedSuccessfullyWhenRetrieveRolloverErrors(VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();

    LedgerFiscalYearRolloverError error = new LedgerFiscalYearRolloverError().withId(id);

    Results<LedgerFiscalYearRolloverError> results = new Results<>();
    results.setResults(List.of(error));
    doReturn(Future.succeededFuture(results)).when(conn)
      .get(eq(LEDGER_FISCAL_YEAR_ROLLOVER_ERRORS_TABLE), eq(LedgerFiscalYearRolloverError.class), eq(criterion), anyBoolean());

    testContext.assertComplete(rolloverErrorDAO.get(criterion, conn))
      .onComplete(event -> {
        testContext.verify(() -> {
          assertThat(event.result(), hasSize(1));
          assertEquals(error, event.result().get(0));
        });

        testContext.completeNow();
      });
  }

}
