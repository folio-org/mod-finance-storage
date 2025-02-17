package org.folio.service;

import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.impl.RowImpl;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.impl.RowDesc;
import org.folio.rest.persist.helpers.LocalRowDesc;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.folio.rest.persist.interfaces.Results;

import java.util.List;

public class ServiceTestUtils {

  public static <T> RowSet<Row> createRowSet(List<T> list) {
    RowDesc rowDesc = new LocalRowDesc(List.of("foo"));
    List<Row> rows = list.stream().map(item -> {
      Row row = new RowImpl(rowDesc);
      row.addJsonObject(JsonObject.mapFrom(item));
      return row;
    }).toList();
    return new LocalRowSet(list.size())
      .withRows(rows);
  }

  public static <T> Results<T> createResults(List<T> list) {
    Results<T> results = new Results<>();
    results.setResults(list);
    return results;
  }

}
