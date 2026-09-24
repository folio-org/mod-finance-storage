package org.folio.service.audit;

/**
 * Outbox payload wrapper carrying both the post-change entity and, for edits, its pre-change state.
 *
 * @param <T> the entity type
 */
public class AuditEntityWrapper<T> {

  private T entity;
  private T originalEntity;

  public AuditEntityWrapper() {
  }

  public AuditEntityWrapper(T entity, T originalEntity) {
    this.entity = entity;
    this.originalEntity = originalEntity;
  }

  public static <T> AuditEntityWrapper<T> of(T entity, T originalEntity) {
    return new AuditEntityWrapper<>(entity, originalEntity);
  }

  public T getEntity() {
    return entity;
  }

  public void setEntity(T entity) {
    this.entity = entity;
  }

  public T getOriginalEntity() {
    return originalEntity;
  }

  public void setOriginalEntity(T originalEntity) {
    this.originalEntity = originalEntity;
  }

}
