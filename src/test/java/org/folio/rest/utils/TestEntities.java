package org.folio.rest.utils;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.FundType;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.GroupFundFiscalYear;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageBudgets;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYears;
import org.folio.rest.jaxrs.resource.FinanceStorageFundDistributions;
import org.folio.rest.jaxrs.resource.FinanceStorageFundTypes;
import org.folio.rest.jaxrs.resource.FinanceStorageFunds;
import org.folio.rest.jaxrs.resource.FinanceStorageGroupFundFiscalYears;
import org.folio.rest.jaxrs.resource.FinanceStorageGroups;
import org.folio.rest.jaxrs.resource.FinanceStorageLedgers;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;
import org.folio.rest.persist.HelperUtils;

public enum TestEntities {
  //The Order is important because of the foreign key relationships
  FISCAL_YEAR(HelperUtils.getEndpoint(FinanceStorageFiscalYears.class), FiscalYear.class, "data/fiscal-years/", "fy20.json", "name", "FY20", 3),
  LEDGER(HelperUtils.getEndpoint(FinanceStorageLedgers.class), Ledger.class, "data/ledgers/", "One-time.json", "code", "One-time", 3),
  FUND_TYPE(HelperUtils.getEndpoint(FinanceStorageFundTypes.class), FundType.class, "data/fund-types/", "approvals.json", "name", "New type name", 26),
  FUND(HelperUtils.getEndpoint(FinanceStorageFunds.class), Fund.class, "data/funds/", "AFRICAHIST.json", "name", "African History", 21),
  BUDGET(HelperUtils.getEndpoint(FinanceStorageBudgets.class), Budget.class, "data/budgets/", "AFRICAHIST-FY20.json", "name", "AFRICAHIST-FY20", 21),
  TRANSACTION(HelperUtils.getEndpoint(FinanceStorageTransactions.class), Transaction.class, "data/transactions/", "allocations/allocation1_AFRICAHIST-FY20.json", "source", "Voucher", 29),
  FUND_DISTRIBUTION(HelperUtils.getEndpoint(FinanceStorageFundDistributions.class), FundDistribution.class, "", "fund_distribution.sample", "currency", "CAD", 0),
  GROUP(HelperUtils.getEndpoint(FinanceStorageGroups.class), Group.class, "data/groups/", "HIST.json", "name", "New name", 1),
  GROUP_FUND_FY(HelperUtils.getEndpoint(FinanceStorageGroupFundFiscalYears.class), GroupFundFiscalYear.class, "data/group-fund-fiscal-years/", "AFRICAHIST-FY20.json", "fundId", "7fbd5d84-62d1-44c6-9c45-6cb173998bbd", 12);


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
