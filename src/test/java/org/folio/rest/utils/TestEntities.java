package org.folio.rest.utils;

import javax.ws.rs.Path;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.FundDistribution;
import org.folio.rest.jaxrs.model.Ledger;
import org.folio.rest.jaxrs.model.Transaction;
import org.folio.rest.jaxrs.resource.FinanceStorageBudgets;
import org.folio.rest.jaxrs.resource.FinanceStorageEncumbrances;
import org.folio.rest.jaxrs.resource.FinanceStorageFiscalYears;
import org.folio.rest.jaxrs.resource.FinanceStorageFundDistributions;
import org.folio.rest.jaxrs.resource.FinanceStorageFunds;
import org.folio.rest.jaxrs.resource.FinanceStorage;
import org.folio.rest.jaxrs.resource.FinanceStorageTransactions;

public enum TestEntities {
  FISCAL_YEAR(getEndpoint(FinanceStorageFiscalYears.class), FiscalYear.class, "data/fiscal-years/", "fy1.json", "name", "FY19", 1),
  LEDGER(getEndpoint(FinanceStorage.class), Ledger.class, "data/ledgers/", "One-time.json", "code", "One-time", 2),
  FUND(getEndpoint(FinanceStorageFunds.class), Fund.class, "data/funds/", "AFRICAHIST.json", "name", "African History", 21),
  BUDGET(getEndpoint(FinanceStorageBudgets.class), Budget.class, "data/budgets/", "AFRICAHIST-FY19.json", "name", "AFRICAHIST-FY19", 21),
  ENCUMBRANCE(getEndpoint(FinanceStorageEncumbrances.class), Encumbrance.class, "data/encumbrances/","encumbrance-8114807d.json", "status", "Released", 2),
  TRANSACTION(getEndpoint(FinanceStorageTransactions.class), Transaction.class, "", "transaction.sample", "note", "PO_Line: The History of Incas", 0),
  FUND_DISTRIBUTION(getEndpoint(FinanceStorageFundDistributions.class), FundDistribution.class, "", "fund_distribution.sample", "currency", "CAD", 0);


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
