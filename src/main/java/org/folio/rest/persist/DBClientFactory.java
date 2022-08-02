package org.folio.rest.persist;

import org.folio.rest.core.model.RequestContext;

public class DBClientFactory {
  public DBClient getDbClient(RequestContext requestContext) {
    return new DBClient(requestContext);
  }
}
