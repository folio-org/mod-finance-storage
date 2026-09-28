package org.folio.utils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonObject;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AuditUtils {

  private static final String METADATA_FIELD = "metadata";
  private static final String VERSION_FIELD = "_version";

  /**
   * Metadata and version are ignored in the comparison, because batch updates refresh them even when nothing else
   * is changed: metadata is set by the update itself, and the optimistic locking trigger increments the version.
   */
  public static <T> List<T> getChangedEntities(List<T> entities, List<T> originalEntities, Function<T, String> idGetter) {
    Map<String, T> originalsById = originalEntities.stream().collect(Collectors.toMap(idGetter, Function.identity()));
    return entities.stream()
      .filter(entity -> isChanged(entity, originalsById.get(idGetter.apply(entity))))
      .toList();
  }

  private static boolean isChanged(Object entity, Object originalEntity) {
    if (originalEntity == null) {
      return true;
    }
    var json = JsonObject.mapFrom(entity);
    var originalJson = JsonObject.mapFrom(originalEntity);
    json.remove(METADATA_FIELD);
    json.remove(VERSION_FIELD);
    originalJson.remove(METADATA_FIELD);
    originalJson.remove(VERSION_FIELD);
    return !json.equals(originalJson);
  }

}
