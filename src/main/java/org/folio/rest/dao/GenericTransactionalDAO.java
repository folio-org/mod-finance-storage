package org.folio.rest.dao;

import org.folio.rest.jaxrs.model.Entity;
import org.folio.rest.persist.Tx;

import io.vertx.core.Future;

/**
 * Generic data access object
 * @param <T> type of the entity
 * @param <E> type of the collection of T entities
 */
public interface GenericTransactionalDAO<T extends Entity, E> {

  /**
   * Searches for T entities
   *
   * @param query   query string to filter records based on matching criteria in fields
   * @param limit   maximum number of results to return
   * @param offset  starting index in a list of results
   * @return Future with E, a collection of T entities
   */
  Future<E> get(String query, int offset, int limit, Tx tx);

  /**
   * Searches for T entity by id
   *
   * @param id  config entity id
   * @return    Future with T entity
   */
  Future<T> getById(String id, Tx tx);

  /**
   * Saves T entity
   *
   * @param entity  entity to save
   * @return Future with created T entity
   */
  Future<T> save(T entity, Tx tx);

  /**
   * Updates T entity
   *
   * @param id      entity id
   * @param entity  entity to update
   * @return CompletableFuture
   */
  Future<Void> update(String id, T entity, Tx tx);

  /**
   * Delete entity by id
   *
   * @param id  entity id
   * @return  CompletableFuture
   */
  Future<Void> delete(String id, Tx tx);

}
