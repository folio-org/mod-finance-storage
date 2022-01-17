package org.folio.service.transactions.cancel;

import org.folio.dao.transactions.TransactionDAO;
import org.folio.rest.persist.DBClient;
import org.folio.service.budget.BudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.when;

class CancelTransactionServiceTest {
  private static final String TENANT_ID = "tenant";

  @InjectMocks
  CancelTransactionService cancelTransactionService;
  @Mock
  TransactionDAO transactionsDAO;
  @Mock
  BudgetService budgetService;
  @Mock
  private DBClient client;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.openMocks(this);
    when(client.getTenantId()).thenReturn(TENANT_ID);
  }

  @Test
  @DisplayName("Transactions should be closed")
  void transactionsClosed() {
  }
}
