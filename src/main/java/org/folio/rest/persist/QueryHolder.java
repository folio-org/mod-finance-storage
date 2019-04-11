package org.folio.rest.persist;

import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.cql.CQLWrapper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

public class QueryHolder {

  private String table;
  private String query;
  private int offset;
  private int limit;
  private String lang;

  public QueryHolder(String table, String query, int offset, int limit, String lang) {
    this.table = table;
    this.query = query;
    this.offset = offset;
    this.limit = limit;
    this.lang = lang;
  }

  public String getTable() {

    return table;
  }

  public String getQuery() {
    return query;
  }

  public int getOffset() {
    return offset;
  }

  public int getLimit() {
    return limit;
  }

  public String getLang() {
    return lang;
  }

  public CQLWrapper buildCQLQuery() throws FieldException {
    CQL2PgJSON cql2PgJSON = new CQL2PgJSON(String.format("%s.jsonb", table));
    return new CQLWrapper(cql2PgJSON, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }
}
