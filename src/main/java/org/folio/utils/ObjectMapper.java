package org.folio.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.UncheckedIOException;
import org.folio.dbschema.ObjectMapperTool;

public final class ObjectMapper {
  private ObjectMapper() {
  }

  public static String valueAsString(Object o) {
    try {
      return ObjectMapperTool.getMapper().writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
