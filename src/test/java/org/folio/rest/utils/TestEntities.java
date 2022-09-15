package org.folio.rest.utils;

import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.resource.*;
import org.folio.rest.persist.HelperUtils;

public enum TestEntities {
  //The Order is important because of the foreign key relationships
  EXPENSE_CLASS(HelperUtils.getEndpoint(FinanceStorageExpenseClasses.class), ExpenseClass.class, "data/expense-classes/", "elec.json", "name", "Electronic", 2, true),
  FISCAL_YEAR(HelperUtils.getEndpoint(FinanceStorageFiscalYears.class), FiscalYear.class, "data/fiscal-years/", "fy22.json", "name", "FY22", 5, true),
  LEDGER(HelperUtils.getEndpoint(FinanceStorageLedgers.class), Ledger.class, "data/ledgers/", "One-time.json", "code", "One-time", 3, true),
  FUND_TYPE(HelperUtils.getEndpoint(FinanceStorageFundTypes.class), FundType.class, "data/fund-types/", "approvals.json", "name", "New type name", 26, true),
  FUND(HelperUtils.getEndpoint(FinanceStorageFunds.class), Fund.class, "data/funds/", "AFRICAHIST.json", "name", "African History", 21, true),
  BUDGET(HelperUtils.getEndpoint(FinanceStorageBudgets.class), Budget.class, "data/budgets/", "AFRICAHIST-FY22.json", "name", "AFRICAHIST-FY22", 21, true),
  BUDGET_EXPENSE_CLASS(HelperUtils.getEndpoint(FinanceStorageBudgetExpenseClasses.class), BudgetExpenseClass.class, "data/budget-expense-classes/", "AFRICAHIST-FY22-elec.json", "status", "Inactive", 1, true),
  TRANSACTION(HelperUtils.getEndpoint(FinanceStorageTransactions.class), Transaction.class, "data/transactions/", "allocations/allocation_AFRICAHIST-FY22.json", "source", "Invoice", 16, true),
  GROUP(HelperUtils.getEndpoint(FinanceStorageGroups.class), Group.class, "data/groups/", "HIST.json", "name", "New name", 1, true),
  GROUP_FUND_FY(HelperUtils.getEndpoint(FinanceStorageGroupFundFiscalYears.class), GroupFundFiscalYear.class, "data/group-fund-fiscal-years/", "AFRICAHIST-FY22.json", "fundId", "7fbd5d84-62d1-44c6-9c45-6cb173998bbd", 12, true),
  LEDGER_FISCAL_YEAR_ROLLOVER(HelperUtils.getEndpoint(FinanceStorageLedgerRollovers.class), LedgerFiscalYearRollover.class, "data/ledger-fiscal-year-rollovers/", "main-library.json", "restrictEncumbrance", "true", 0, true),
  LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS(HelperUtils.getEndpoint(FinanceStorageLedgerRolloversProgress.class), LedgerFiscalYearRolloverProgress.class, "data/ledger-fiscal-year-rollovers/", "main-library-progress.json", "financialRolloverStatus", "Success", 0, false),
  LEDGER_FISCAL_YEAR_ROLLOVER_ERROR(HelperUtils.getEndpoint(FinanceStorageLedgerRolloversErrors.class), LedgerFiscalYearRolloverError.class, "data/ledger-fiscal-year-rollovers/", "main-library-errors.json", "errorType", "Fund", 0, false);


  TestEntities(String endpoint, Class<?> clazz, String pathToSamples, String sampleFileName, String updatedFieldName,
      String updatedFieldValue, int initialQuantity, boolean isOptLockingEnabled) {
    this.endpoint = endpoint;
    this.clazz = clazz;
    this.sampleFileName = sampleFileName;
    this.pathToSamples = pathToSamples;
    this.updatedFieldName = updatedFieldName;
    this.updatedFieldValue = updatedFieldValue;
    this.initialQuantity = initialQuantity;
    this.isOptLockingEnabled = isOptLockingEnabled;
  }

  private int initialQuantity;
  private String endpoint;
  private String sampleFileName;
  private String sampleId;
  private Integer version;
  private String pathToSamples;
  private String updatedFieldName;
  private String updatedFieldValue;
  private Class<?> clazz;
  private boolean isOptLockingEnabled;

  public String getEndpoint() {
    return endpoint;
  }

  public String getEndpointWithId() {
    return endpoint + "/{id}";
  }

  public String getUpdatedFieldName() {
    return updatedFieldName;
  }

  public String getUpdatedFieldValue() {
    return updatedFieldValue;
  }

  public int getInitialQuantity() {
    return initialQuantity;
  }

  public String getSampleFileName() {
    return pathToSamples + sampleFileName;
  }

  public Class<?> getClazz() {
    return clazz;
  }

  public String getPathToSampleFile() {
    return pathToSamples + sampleFileName;
  }

  public String getId() {
    return sampleId;
  }

  public void setId(String id) {
    this.sampleId = id;
  }

  public boolean getOptimisticLockingEnabledValue() {
    return isOptLockingEnabled;
  }

}
