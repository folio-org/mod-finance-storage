package org.folio.rest.utils;

import lombok.Getter;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.resource.*;
import org.folio.rest.persist.HelperUtils;

@Getter
public enum TestEntities {
  //The Order is important because of the foreign key relationships
  EXPENSE_CLASS(HelperUtils.getEndpoint(FinanceStorageExpenseClasses.class), ExpenseClass.class, "data/expense-classes-4.0.0/", "elec.json", "name", "Electronic", 2, true),
  FISCAL_YEAR(HelperUtils.getEndpoint(FinanceStorageFiscalYears.class), FiscalYear.class, "data/fiscal-years-8.4.0/", "fy25.json", "name", "FY25", 7, true),
  LEDGER(HelperUtils.getEndpoint(FinanceStorageLedgers.class), Ledger.class, "data/ledgers-8.4.0/", "One-time.json", "code", "One-time", 5, true),
  FUND_TYPE(HelperUtils.getEndpoint(FinanceStorageFundTypes.class), FundType.class, "data/fund-types-6.0.0/", "approvals.json", "name", "New type name", 26, true),
  FUND(HelperUtils.getEndpoint(FinanceStorageFunds.class), Fund.class, "data/funds-8.4.0/", "AFRICAHIST.json", "name", "African History", 23, true),
  BUDGET(HelperUtils.getEndpoint(FinanceStorageBudgets.class), Budget.class, "data/budgets-8.4.0/", "AFRICAHIST-FY25.json", "name", "AFRICAHIST-FY25", 24, true),
  BUDGET_EXPENSE_CLASS(HelperUtils.getEndpoint(FinanceStorageBudgetExpenseClasses.class), BudgetExpenseClass.class, "data/budget-expense-classes-8.4.0/", "AFRICAHIST-FY25-elec.json", "status", "Inactive", 1, true),
  GROUP(HelperUtils.getEndpoint(FinanceStorageGroups.class), Group.class, "data/groups-3.2.0/", "HIST.json", "name", "New name", 2, true),
  GROUP_FUND_FY(HelperUtils.getEndpoint(FinanceStorageGroupFundFiscalYears.class), GroupFundFiscalYear.class, "data/group-fund-fiscal-years-8.4.0/", "AFRICAHIST-FY25.json", "fundId", "7fbd5d84-62d1-44c6-9c45-6cb173998bbd", 13, true),
  LEDGER_FISCAL_YEAR_ROLLOVER(HelperUtils.getEndpoint(FinanceStorageLedgerRollovers.class), LedgerFiscalYearRollover.class, "data/ledger-fiscal-year-rollovers/", "main-library.json", "restrictEncumbrance", "true", 0, true),
  LEDGER_FISCAL_YEAR_ROLLOVER_LOG(HelperUtils.getEndpoint(FinanceStorageLedgerRolloversLogs.class), LedgerFiscalYearRolloverLog.class, null, null, null, null, 0, false),
  LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS(HelperUtils.getEndpoint(FinanceStorageLedgerRolloversProgress.class), LedgerFiscalYearRolloverProgress.class, "data/ledger-fiscal-year-rollovers/", "main-library-progress.json", "financialRolloverStatus", "Success", 0, false),
  LEDGER_FISCAL_YEAR_ROLLOVER_ERROR(HelperUtils.getEndpoint(FinanceStorageLedgerRolloversErrors.class), LedgerFiscalYearRolloverError.class, "data/ledger-fiscal-year-rollovers/", "main-library-errors.json", "errorType", "Fund", 0, false),
  FUND_UPDATE_LOG(HelperUtils.getEndpoint(FinanceStorageFundUpdateLogs.class), FundUpdateLog.class, "data/fund-update-log/", "monthly-fund-update.json", "jobName", "Weekly Fund Update", 0, true);

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

  private final int initialQuantity;
  private final String endpoint;
  private final String sampleFileName;
  private String sampleId;
  private final String pathToSamples;
  private final String updatedFieldName;
  private final String updatedFieldValue;
  private final Class<?> clazz;
  private final boolean isOptLockingEnabled;

  public String getEndpointWithId() {
    return endpoint + "/{id}";
  }

  public String getSampleFileName() {
    return pathToSamples + sampleFileName;
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
