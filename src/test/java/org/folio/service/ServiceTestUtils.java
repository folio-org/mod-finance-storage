package org.folio.service;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.desc.ColumnDescriptor;
import io.vertx.sqlclient.impl.RowBase;
import io.vertx.sqlclient.internal.RowDescriptorBase;
import org.folio.rest.persist.helpers.LocalColumnDescriptor;
import org.folio.rest.persist.helpers.LocalRowSet;
import org.folio.rest.persist.interfaces.Results;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

public class ServiceTestUtils {

  public static RowDescriptorBase createRowDesc(String... columnNames) {
    ColumnDescriptor[] descriptors = Arrays.stream(columnNames)
      .map(LocalColumnDescriptor::new)
      .toArray(ColumnDescriptor[]::new);
    return new RowDescriptorBase(descriptors);
  }

  public static <T> RowSet<Row> createRowSet(List<T> list) {
    var rowDesc = createRowDesc("foo");
    List<Row> rows = list.stream().map(item -> {
      Row row = new RowBase(rowDesc);
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

  public static <T, C> T callPrivateMethod(C object, String methodName, Class<T> returnType, Class<?>[] paramTypes, Object[] params)
    throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    var method = object.getClass().getDeclaredMethod(methodName, paramTypes);
    method.setAccessible(true);
    return returnType.cast(method.invoke(object, params));
  }
}
