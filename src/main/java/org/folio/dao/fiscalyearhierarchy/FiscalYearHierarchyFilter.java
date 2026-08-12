package org.folio.dao.fiscalyearhierarchy;

public record FiscalYearHierarchyFilter(
  String fiscalYearId,
  String ledgerStatus,
  String groupStatus,
  String fundStatus,
  String budgetStatus,
  String expenseClassStatus) {
}
