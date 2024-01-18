package org.folio.rest.utils;

import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.resource.*;
import org.folio.rest.persist.HelperUtils;

import static org.folio.rest.impl.TransactionTest.BATCH_TRANSACTION_ENDPOINT;
import static org.folio.rest.impl.TransactionTest.ORDER_TRANSACTION_SUMMARIES_ENDPOINT;

public enum TestEntities {
  //The Order is important because of the foreign key relationships
  EXPENSE_CLASS(HelperUtils.getEndpoint(FinanceStorageExpenseClasses.class), ExpenseClass.class, "data/expense-classes-4.0.0/", "elec.json", "name", "Electronic", 2, true),
  FISCAL_YEAR(HelperUtils.getEndpoint(FinanceStorageFiscalYears.class), FiscalYear.class, "data/fiscal-years-8.4.0/", "fy24.json", "name", "FY24", 8, true),
  LEDGER(HelperUtils.getEndpoint(FinanceStorageLedgers.class), Ledger.class, "data/ledgers-8.4.0/", "One-time.json", "code", "One-time", 5, true),
  FUND_TYPE(HelperUtils.getEndpoint(FinanceStorageFundTypes.class), FundType.class, "data/fund-types-6.0.0/", "approvals.json", "name", "New type name", 26, true),
  FUND(HelperUtils.getEndpoint(FinanceStorageFunds.class), Fund.class, "data/funds-8.4.0/", "AFRICAHIST.json", "name", "African History", 23, true),
  BUDGET(HelperUtils.getEndpoint(FinanceStorageBudgets.class), Budget.class, "data/budgets-8.4.0/", "AFRICAHIST-FY24.json", "name", "AFRICAHIST-FY24", 23, true),
  BUDGET_EXPENSE_CLASS(HelperUtils.getEndpoint(FinanceStorageBudgetExpenseClasses.class), BudgetExpenseClass.class, "data/budget-expense-classes-8.4.0/", "AFRICAHIST-FY24-elec.json", "status", "Inactive", 1, true),
  ORDER_SUMMARY(ORDER_TRANSACTION_SUMMARIES_ENDPOINT, OrderTransactionSummary.class, "data/order-transaction-summaries/", "order-306857_transaction-summary.json", "numTransactions", "1", 1, false),
  ALLOCATION_TRANSACTION(HelperUtils.getEndpoint(FinanceStorageTransactions.class), Transaction.class, "data/transactions/", "allocations-8.4.0/allocation_AFRICAHIST-FY24.json", "source", "Invoice", 16, true),
  ENCUMBRANCE_TRANSACTION(HelperUtils.getEndpoint(FinanceStorageTransactions.class), Transaction.class, "data/transactions/", "encumbrances/encumbrance_AFRICAHIST_306857_2.json", "source", "Invoice", 16, true),
  GROUP(HelperUtils.getEndpoint(FinanceStorageGroups.class), Group.class, "data/groups-3.2.0/", "HIST.json", "name", "New name", 1, true),
  GROUP_FUND_FY(HelperUtils.getEndpoint(FinanceStorageGroupFundFiscalYears.class), GroupFundFiscalYear.class, "data/group-fund-fiscal-years-8.4.0/", "AFRICAHIST-FY24.json", "fundId", "7fbd5d84-62d1-44c6-9c45-6cb173998bbd", 12, true),
  LEDGER_FISCAL_YEAR_ROLLOVER(HelperUtils.getEndpoint(FinanceStorageLedgerRollovers.class), LedgerFiscalYearRollover.class, "data/ledger-fiscal-year-rollovers/", "main-library.json", "restrictEncumbrance", "true", 0, true),
  LEDGER_FISCAL_YEAR_ROLLOVER_LOG(HelperUtils.getEndpoint(FinanceStorageLedgerRolloversLogs.class), LedgerFiscalYearRolloverLog.class, null, null, null, null, 0, false),
  LEDGER_FISCAL_YEAR_ROLLOVER_PROGRESS(HelperUtils.getEndpoint(FinanceStorageLedgerRolloversProgress.class), LedgerFiscalYearRolloverProgress.class, "data/ledger-fiscal-year-rollovers/", "main-library-progress.json", "financialRolloverStatus", "Success", 0, false),
  LEDGER_FISCAL_YEAR_ROLLOVER_ERROR(HelperUtils.getEndpoint(FinanceStorageLedgerRolloversErrors.class), LedgerFiscalYearRolloverError.class, "data/ledger-fiscal-year-rollovers/", "main-library-errors.json", "errorType", "Fund", 0, false),
  TRANSACTION_BATCH_WITH_PATCH(BATCH_TRANSACTION_ENDPOINT, Batch.class, "data/transactions/", "batch/batch_with_patch.json", null, null, 0, false);


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
