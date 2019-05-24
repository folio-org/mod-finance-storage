package org.folio.rest.impl;

import java.net.MalformedURLException;

import org.folio.rest.utils.TestEntities;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EntitiesCrudTest extends TestBase {

  @Parameterized.Parameter public TestEntities testEntity;

  @Parameterized.Parameters(name = "{index}:{0}")
  public static TestEntities[] data() {
    return TestEntities.values();
  }

  @Test
  public void testFetchEntityWithNonExistedId() throws MalformedURLException {
    logger.info(String.format("--- %s get by id test: Invalid %s: %s", testEntity.name(), testEntity.name(), NON_EXISTED_ID));
    getDataById(testEntity.getEndpoint(), NON_EXISTED_ID).then().log().ifValidationFails()
      .statusCode(404);
  }

  @Test
  public void testEditEntityWithNonExistedId() throws MalformedURLException {
    logger.info(String.format("--- %s put by id test: Invalid %s: %s", testEntity.name(), testEntity.name(), NON_EXISTED_ID));
    String sampleData = getFile(testEntity.getPathToSampleFile());
    putData(testEntity.getEndpoint(), NON_EXISTED_ID, sampleData)
      .then().log().ifValidationFails()
        .statusCode(404);
  }

  @Test
  public void testDeleteEntityWithNonExistedId() throws MalformedURLException {
    logger.info(String.format("--- %s delete by id test: Invalid %s: %s", testEntity.name(), testEntity.name(), NON_EXISTED_ID));
    deleteData(testEntity.getEndpoint(), NON_EXISTED_ID)
      .then().log().ifValidationFails()
        .statusCode(404);
  }

  @Test
  public void testGetEntitiesWithInvalidCQLQuery() throws MalformedURLException {
    logger.info(String.format("--- %s test: Invalid CQL query", testEntity.name()));
    testInvalidCQLQuery(testEntity.getEndpoint() + "?query=invalid-query");
  }

}
