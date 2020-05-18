package org.folio.rest.dao;

import io.vertx.core.Future;
import org.folio.rest.jaxrs.model.Entity;
import org.folio.rest.persist.Tx;

public abstract class GenericPostgresTransactionalDAO<T extends Entity, E> implements GenericTransactionalDAO<T, E> {
  @Override
  public Future<E> get(String sqlQuery, int offset, int limit, Tx tx) {
    return null;
  }

  @Override
  public Future<T> getById(String id, Tx tx) {
    return null;
  }

  @Override
  public Future<T> save(T entity, Tx tx) {
    return null;
  }

  @Override
  public Future<Void> update(String id, T entity, Tx tx) {
    return null;
  }

  @Override
  public Future<Void> delete(String id, Tx tx) {
    return null;
  }

  protected abstract String getTableName();

  protected abstract Class<T> getClazz();

  protected abstract Class<E> getCollectionClazz();
}
