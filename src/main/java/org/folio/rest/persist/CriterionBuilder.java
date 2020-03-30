package org.folio.rest.persist;

import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.GroupedCriterias;

public class CriterionBuilder {

  private static final String QUOTES = "'%s'";

  private GroupedCriterias groupedCriterias;
  private String operation;

  public CriterionBuilder() {
    operation = "AND";
    groupedCriterias = new GroupedCriterias();
  }

  public CriterionBuilder(String op) {
    operation = op;
    groupedCriterias = new GroupedCriterias();
  }

  public CriterionBuilder withOperation(String operation) {
    this.operation = operation;
    return this;
  }

  public CriterionBuilder withJson(String fieldName, String withOp ,String fieldValue) {
    groupedCriterias.addCriteria(getCriteria(String.format(QUOTES, fieldName) , withOp, fieldValue), operation);
    return this;
  }

  public CriterionBuilder with(String fieldName, String fieldValue) {
    groupedCriterias.addCriteria(getCriteria(fieldName, "=", fieldValue).setJSONB(false), operation);
    return this;
  }

  public Criterion build() {
    return new Criterion().addGroupOfCriterias(groupedCriterias);
  }

  private Criteria getCriteria(String fieldName, String operation, String fieldValue) {
    Criteria a = new Criteria();
    a.addField(fieldName);
    a.setOperation(operation);
    a.setVal(fieldValue);
    return a;
  }
}
