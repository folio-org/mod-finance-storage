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

  /**
   * Returns the entities that differ from their original state, so that audit events are not sent for entities
   * a batch update did not really change. Metadata is ignored in the comparison, because it is refreshed
   * on every batch update. An entity without an original is considered changed.
   *
   * @param entities         the entities (post-change state)
   * @param originalEntities the entities before the change, matched by id
   * @param idGetter         the entity id getter
   * @return the changed entities
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
    originalJson.remove(METADATA_FIELD);
    return !json.equals(originalJson);
  }

}
