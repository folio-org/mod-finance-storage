package org.folio.service.transactions.cancel;

import org.folio.dao.transactions.EncumbranceDAO;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CancelPaymentCreditServiceTest {
  private static final String TENANT_ID = "tenant";

  @InjectMocks
  private CancelPaymentCreditService cancelPaymentCreditService;

  @Mock
  private BudgetService budgetService;
  @Mock
  private EncumbranceDAO transactionsDAO;
  @Mock
  private DBClient client;

  @BeforeEach
  public void initMocks(){
    MockitoAnnotations.openMocks(this);
    when(client.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test void cancelTransactions() {
  }
}
