package org.folio.rest.impl;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.StorageTestSuite;
import org.folio.rest.jaxrs.model.LedgerFiscalYearRolloverBudget;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgerRolloversBudgets;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import static org.folio.rest.impl.LegderRolloverBudgetAPI.LEDGER_FISCAL_YEAR_ROLLOVER_BUDGETS_TABLE;

@ExtendWith(VertxExtension.class)
public class LedgerRolloverBudgetTest extends TestBase {

  private static final String BUDGET_ID = UUID.randomUUID().toString();
  private static final String ENTITY_NAME = "LEDGER_FISCAL_YEAR_ROLLOVER_BUDGET";
  private static final String LEDGER_FISCAL_YEAR_ROLLOVER_BUDGET_ENDPOINT = HelperUtils.getEndpoint(FinanceStorageLedgerRolloversBudgets.class);

  @BeforeAll
  static void saveFiscalYearRolloverBudgetData(VertxTestContext context) {
    PostgresClient.getInstance(StorageTestSuite.getVertx(), TENANT_NAME)
      .saveAndReturnUpdatedEntity(LEDGER_FISCAL_YEAR_ROLLOVER_BUDGETS_TABLE, BUDGET_ID, new LedgerFiscalYearRolloverBudget())
      .onComplete(context.succeedingThenComplete());
  }

  @Test
  void testGetCollection() {
    logger.info(String.format("--- mod-finance-storage %s test: Verifying only 1 adjustment was created ... ", ENTITY_NAME));
    verifyCollectionQuantity(LEDGER_FISCAL_YEAR_ROLLOVER_BUDGET_ENDPOINT, 1);
  }

  @Test
  void testGetById() {
    logger.info(String.format("--- mod-finance-storage %1$s test: Fetching %1$s with ID %2$s", ENTITY_NAME, BUDGET_ID));
    testEntitySuccessfullyFetched(LEDGER_FISCAL_YEAR_ROLLOVER_BUDGET_ENDPOINT + "/{id}", BUDGET_ID);
  }

}
