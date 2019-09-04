package org.folio.rest.utils;

import javax.ws.rs.Path;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.FundType;
import org.folio.rest.jaxrs.model.Group;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageBudgets;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYears;
import org.folio.rest.jaxrs.resource.FinanceStorageFundDistributions;
import org.folio.rest.jaxrs.resource.FinanceStorageFundTypes;
import org.folio.rest.jaxrs.resource.FinanceStorageFunds;
import org.folio.rest.jaxrs.resource.FinanceStorage;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;

public enum TestEntities {
  FISCAL_YEAR(getEndpoint(FinanceStorageFiscalYears.class), FiscalYear.class, "data/fiscal-years/", "fy19.json", "name", "FY19", 2),
  LEDGER(getEndpoint(FinanceStorage.class), Ledger.class, "data/ledgers/", "One-time.json", "code", "One-time", 2),
  FUND(getEndpoint(FinanceStorageFunds.class), Fund.class, "data/funds/", "AFRICAHIST.json", "name", "African History", 21),
  FUND_TYPE(getEndpoint(FinanceStorageFundTypes.class), FundType.class, "", "fund_type.sample", "name", "New type name", 0),
  BUDGET(getEndpoint(FinanceStorageBudgets.class), Budget.class, "data/budgets/", "AFRICAHIST-FY19.json", "name", "AFRICAHIST-FY19", 21),
  TRANSACTION(getEndpoint(FinanceStorageTransactions.class), Transaction.class, "data/transactions/", "payment.json", "source", "Voucher", 5),
  FUND_DISTRIBUTION(getEndpoint(FinanceStorageFundDistributions.class), FundDistribution.class, "", "fund_distribution.sample", "currency", "CAD", 0),
  GROUP(getEndpoint(FinanceStorageGroups.class), Group.class, "data/groups/", "HIST.json", "name", "New name", 1);


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

  public Class<?> getClazz() {
    return clazz;
  }

  public String getPathToSampleFile() {
    return pathToSamples + sampleFileName;
  }

  private static String getEndpoint(Class<?> clazz) {
    return clazz.getAnnotation(Path.class).value();
  }
}
