package org.folio.service.fiscalyear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.folio.dao.fiscalyear.FiscalYearHierarchyFlatRow;
import org.folio.rest.jaxrs.model.FiscalYearHierarchy;
import org.junit.jupiter.api.Test;

class FiscalYearHierarchyBuilderTest {

  private static final String FY = "ac2164c7-ba3d-41c2-a12c-e35ceccbfaf2";
  private static final String LEDGER = "7cef8378-7cbd-4fae-bcdd-8b9d7c0af9de";
  private static final String GROUP = "a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d";
  private static final String FUND = "b2c3d4e5-f6a7-5b6c-9d0e-1f2a3b4c5d6a";
  private static final String BUDGET = "c3d4e5f6-a7b8-4c7d-8e1f-2a3b4c5d6e7a";
  private static final String EC1 = "d4e5f6a7-b8c9-4d8e-9f2a-3b4c5d6e7f8a";

  @Test
  void buildsGroupedTree() {
    var r1 = row(GROUP, EC1, "Books", "BK");
    var r2 = row(GROUP, "e5f6a7b8-c9d0-4e9f-a2b3-b4c5d6e7f8a9", "Serials", "SER");
    FiscalYearHierarchy h = FiscalYearHierarchyBuilder.build(FY, List.of(r1, r2));
    assertEquals(FY, h.getFiscalYearId());
    assertEquals(1, h.getLedgers().size());
    assertEquals(1, h.getLedgers().get(0).getGroups().size());
    assertEquals(1, h.getLedgers().get(0).getGroups().get(0).getFunds().size());
    assertEquals(1, h.getLedgers().get(0).getGroups().get(0).getFunds().get(0).getBudgets().size());
    assertEquals(2,
      h.getLedgers().get(0).getGroups().get(0).getFunds().get(0).getBudgets().get(0).getExpenseClasses().size());
  }

  @Test
  void ungroupedFundUsesUngroupedFunds() {
    var r = row(null, EC1, "Books", "BK");
    FiscalYearHierarchy h = FiscalYearHierarchyBuilder.build(FY, List.of(r));
    assertTrue(h.getLedgers().get(0).getGroups().isEmpty());
    assertEquals(1, h.getLedgers().get(0).getUngroupedFunds().size());
    assertEquals(FUND, h.getLedgers().get(0).getUngroupedFunds().get(0).getId());
  }

  @Test
  void budgetWithoutExpenseClassStillAppears() {
    var r = row(GROUP, null, null, null);
    FiscalYearHierarchy h = FiscalYearHierarchyBuilder.build(FY, List.of(r));
    var budgets = h.getLedgers().get(0).getGroups().get(0).getFunds().get(0).getBudgets();
    assertEquals(1, budgets.size());
    assertTrue(budgets.get(0).getExpenseClasses().isEmpty());
  }

  private static FiscalYearHierarchyFlatRow row(String groupId, String ecId, String ecName, String ecCode) {
    var row = new FiscalYearHierarchyFlatRow();
    row.setFiscalYearId(FY);
    row.setLedgerId(LEDGER);
    row.setLedgerName("L");
    row.setLedgerCode("LC");
    row.setGroupId(groupId);
    row.setGroupName(groupId != null ? "G" : null);
    row.setGroupCode(groupId != null ? "GC" : null);
    row.setFundId(FUND);
    row.setFundName("F");
    row.setFundCode("FC");
    row.setBudgetId(BUDGET);
    row.setBudgetName("B");
    row.setExpenseClassId(ecId);
    row.setExpenseClassName(ecName);
    row.setExpenseClassCode(ecCode);
    return row;
  }
}
