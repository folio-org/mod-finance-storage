package org.folio.rest.persist;

import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;

public class CriterionBuilder {

  private Criteria criteria;

  public CriterionBuilder() {
    criteria = new Criteria();
  }

  public CriterionBuilder with(String filedName, String fieldValue) {
    criteria.addField("'" + filedName + "'")
      .setOperation("=")
      .setVal(fieldValue);
    return this;
  }

  public Criterion build() {
    return new Criterion(criteria);
  }
}
