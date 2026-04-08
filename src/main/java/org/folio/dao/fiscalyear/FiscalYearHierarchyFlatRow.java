package org.folio.dao.fiscalyear;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One row from {@code fiscal_year_hierarchy_view} (flat join), aggregated into {@link org.folio.rest.jaxrs.model.FiscalYearHierarchy} in Java.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FiscalYearHierarchyFlatRow {

  @JsonProperty("fiscalYearId")
  private String fiscalYearId;
  @JsonProperty("ledgerId")
  private String ledgerId;
  @JsonProperty("ledgerName")
  private String ledgerName;
  @JsonProperty("ledgerCode")
  private String ledgerCode;
  @JsonProperty("groupId")
  private String groupId;
  @JsonProperty("groupName")
  private String groupName;
  @JsonProperty("groupCode")
  private String groupCode;
  @JsonProperty("fundId")
  private String fundId;
  @JsonProperty("fundName")
  private String fundName;
  @JsonProperty("fundCode")
  private String fundCode;
  @JsonProperty("budgetId")
  private String budgetId;
  @JsonProperty("budgetName")
  private String budgetName;
  @JsonProperty("expenseClassId")
  private String expenseClassId;
  @JsonProperty("expenseClassName")
  private String expenseClassName;
  @JsonProperty("expenseClassCode")
  private String expenseClassCode;

  public String getFiscalYearId() {
    return fiscalYearId;
  }

  public void setFiscalYearId(String fiscalYearId) {
    this.fiscalYearId = fiscalYearId;
  }

  public String getLedgerId() {
    return ledgerId;
  }

  public void setLedgerId(String ledgerId) {
    this.ledgerId = ledgerId;
  }

  public String getLedgerName() {
    return ledgerName;
  }

  public void setLedgerName(String ledgerName) {
    this.ledgerName = ledgerName;
  }

  public String getLedgerCode() {
    return ledgerCode;
  }

  public void setLedgerCode(String ledgerCode) {
    this.ledgerCode = ledgerCode;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public String getGroupName() {
    return groupName;
  }

  public void setGroupName(String groupName) {
    this.groupName = groupName;
  }

  public String getGroupCode() {
    return groupCode;
  }

  public void setGroupCode(String groupCode) {
    this.groupCode = groupCode;
  }

  public String getFundId() {
    return fundId;
  }

  public void setFundId(String fundId) {
    this.fundId = fundId;
  }

  public String getFundName() {
    return fundName;
  }

  public void setFundName(String fundName) {
    this.fundName = fundName;
  }

  public String getFundCode() {
    return fundCode;
  }

  public void setFundCode(String fundCode) {
    this.fundCode = fundCode;
  }

  public String getBudgetId() {
    return budgetId;
  }

  public void setBudgetId(String budgetId) {
    this.budgetId = budgetId;
  }

  public String getBudgetName() {
    return budgetName;
  }

  public void setBudgetName(String budgetName) {
    this.budgetName = budgetName;
  }

  public String getExpenseClassId() {
    return expenseClassId;
  }

  public void setExpenseClassId(String expenseClassId) {
    this.expenseClassId = expenseClassId;
  }

  public String getExpenseClassName() {
    return expenseClassName;
  }

  public void setExpenseClassName(String expenseClassName) {
    this.expenseClassName = expenseClassName;
  }

  public String getExpenseClassCode() {
    return expenseClassCode;
  }

  public void setExpenseClassCode(String expenseClassCode) {
    this.expenseClassCode = expenseClassCode;
  }
}
