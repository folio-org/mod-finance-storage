package org.folio.rest.persist;

import org.folio.rest.jaxrs.resource.support.ResponseDelegate;

import java.lang.reflect.Method;
import java.util.List;

public class EntitiesMetadataHolder<T, E> {
  private static final String RESPOND_200_WITH_APPLICATION_JSON = "respond200WithApplicationJson";
  private static final String RESPOND_400_WITH_TEXT_PLAIN       = "respond400WithTextPlain";
  private static final String RESPOND_500_WITH_TEXT_PLAIN       = "respond500WithTextPlain";
  private static final String SET_TOTAL_RECORDS_METHOD_NAME     = "setTotalRecords";
  private static final String SET_RESULT_METHOD_NAME_TEMPLATE   = "set%ss";

  private Class<T> clazz;
  private Class<E> collectionClazz;
  private Class<? extends ResponseDelegate> responseDelegateClazz;
  private String setResultMethodName;

  public EntitiesMetadataHolder(Class<T> clazz, Class<E> collectionClazz, Class<? extends ResponseDelegate> responseDelegateClazz) {
    this(clazz, collectionClazz, responseDelegateClazz, String.format(SET_RESULT_METHOD_NAME_TEMPLATE, clazz.getSimpleName()));
  }

  public EntitiesMetadataHolder(Class<T> clazz, Class<E> collectionClazz, Class<? extends ResponseDelegate> responseDelegateClazz, String setResultMethodName) {
    this.clazz = clazz;
    this.collectionClazz = collectionClazz;
    this.responseDelegateClazz = responseDelegateClazz;
    this.setResultMethodName = setResultMethodName;
  }

  public Class<T> getClazz() {
    return clazz;
  }

  public Class<E> getCollectionClazz() {
    return collectionClazz;
  }

  public Method getRespond500WithTextPlainMethod() throws NoSuchMethodException {
    return responseDelegateClazz.getMethod(RESPOND_500_WITH_TEXT_PLAIN, Object.class);
  }

  public Method getRespond400WithTextPlainMethod() throws NoSuchMethodException {
    return responseDelegateClazz.getMethod(RESPOND_400_WITH_TEXT_PLAIN, Object.class);
  }

  public Method getRespond200WithApplicationJson() throws NoSuchMethodException {
    return responseDelegateClazz.getMethod(RESPOND_200_WITH_APPLICATION_JSON, collectionClazz);
  }

  public Method getSetTotalRecordsMethod() throws NoSuchMethodException {
    return collectionClazz.getMethod(SET_TOTAL_RECORDS_METHOD_NAME, Integer.class);
  }

  public Method getSetResultsMethod() throws NoSuchMethodException {
    return collectionClazz.getMethod(setResultMethodName, List.class);
  }
}
