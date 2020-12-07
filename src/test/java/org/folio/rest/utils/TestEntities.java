package org.folio.rest.utils;

import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.resource.*;
import org.folio.rest.persist.HelperUtils;

public enum TestEntities {
  //The Order is important because of the foreign key relationships
  EXPENSE_CLASS(HelperUtils.getEndpoint(FinanceStorageExpenseClasses.class), ExpenseClass.class, "data/expense-classes/", "elec.json", "name", "Electronic", 2),
  FISCAL_YEAR(HelperUtils.getEndpoint(FinanceStorageFiscalYears.class), FiscalYear.class, "data/fiscal-years/", "fy20.json", "name", "FY20", 4),
  LEDGER(HelperUtils.getEndpoint(FinanceStorageLedgers.class), Ledger.class, "data/ledgers/", "One-time.json", "code", "One-time", 3),
  FUND_TYPE(HelperUtils.getEndpoint(FinanceStorageFundTypes.class), FundType.class, "data/fund-types/", "approvals.json", "name", "New type name", 26),
  FUND(HelperUtils.getEndpoint(FinanceStorageFunds.class), Fund.class, "data/funds/", "AFRICAHIST.json", "name", "African History", 21),
  BUDGET(HelperUtils.getEndpoint(FinanceStorageBudgets.class), Budget.class, "data/budgets/", "AFRICAHIST-FY20.json", "name", "AFRICAHIST-FY20", 21),
  BUDGET_EXPENSE_CLASS(HelperUtils.getEndpoint(FinanceStorageBudgetExpenseClasses.class), BudgetExpenseClass.class, "data/budget-expense-classes/", "AFRICAHIST-FY20-elec.json", "status", "Inactive", 1),
  TRANSACTION(HelperUtils.getEndpoint(FinanceStorageTransactions.class), Transaction.class, "data/transactions/", "allocations/allocation1_AFRICAHIST-FY20.json", "source", "Invoice", 17),
  GROUP(HelperUtils.getEndpoint(FinanceStorageGroups.class), Group.class, "data/groups/", "HIST.json", "name", "New name", 1),
  GROUP_FUND_FY(HelperUtils.getEndpoint(FinanceStorageGroupFundFiscalYears.class), GroupFundFiscalYear.class, "data/group-fund-fiscal-years/", "AFRICAHIST-FY20.json", "fundId", "7fbd5d84-62d1-44c6-9c45-6cb173998bbd", 12),
  LEDGER_FISCAL_YEAR_ROLLOVER(HelperUtils.getEndpoint(FinanceStorageLedgerRollovers.class), LedgerFiscalYearRollover.class, "data/ledger-fiscal-year-rollovers/", "main-library.json", "restrictEncumbrance", "true", 0),
  LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS(HelperUtils.getEndpoint(FinanceStorageLedgerRolloversProgress.class), LedgerFiscalYearRolloverProgress.class, "data/ledger-fiscal-year-rollovers/", "main-library-progress.json", "financialRolloverStatus", "Success", 0);


  TestEntities(String endpoint, Class<?> clazz, String pathToSamples, String sampleFileName, String updatedFieldName,
      String updatedFieldValue, int initialQuantity) {
    this.endpoint = endpoint;
    this.clazz = clazz;
    this.sampleFileName = sampleFileName;
    this.pathToSamples = pathToSamples;
    this.updatedFieldName = updatedFieldName;
    this.updatedFieldValue = updatedFieldValue;
    this.initialQuantity = initialQuantity;
  }

  private int initialQuantity;
  private String endpoint;
  private String sampleFileName;
  private String sampleId;
  private String pathToSamples;
  private String updatedFieldName;
  private String updatedFieldValue;
  private Class<?> clazz;

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
}
