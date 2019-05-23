package org.folio.rest.utils;

import org.folio.rest.jaxrs.model.Budget;
import org.folio.rest.jaxrs.model.Encumbrance;
import org.folio.rest.jaxrs.model.FiscalYear;
import org.folio.rest.jaxrs.model.Fund;
import org.folio.rest.jaxrs.model.Ledger;


public enum TestEntities {
  FISCAL_YEAR("/finance-storage/fiscal_year", FiscalYear.class, "data/fiscal-years/", "fy1.json", "name", "FY19", 1),
  LEDGER("/finance-storage/ledger", Ledger.class, "data/ledgers/","One-time.json", "code", "One-time", 2),
  FUND("/finance-storage/fund", Fund.class, "data/funds/","AFRICAHIST.json", "name", "African History", 21),
  BUDGET("/finance-storage/budget", Budget.class, "data/budgets/","AFRICAHIST-FY19.json", "name", "AFRICAHIST-FY19", 21),
  ENCUMBRANCE("/finance-storage/encumbrance", Encumbrance.class, "data/encumbrances/","encumbrance-8114807d.json", "status", "Released", 2);


  TestEntities(String endpoint, Class<?> clazz, String pathToSamples, String sampleFileName, String updatedFieldName, String updatedFieldValue, int initialQuantity) {
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

  public String getSampleFileName() {
    return sampleFileName;
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

  public String getPathToSamples() {
    return pathToSamples;
  }
}
