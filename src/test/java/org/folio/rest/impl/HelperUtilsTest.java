package org.folio.rest.impl;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.persist.HelperUtils.getEndpoint;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.folio.HttpStatus;
import org.folio.rest.jaxrs.resource.FinanceStorageGroupBudgets;
import org.folio.rest.persist.EntitiesMetadataHolder;
import org.folio.rest.persist.HelperUtils;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.Context;
import mockit.Mock;
import mockit.MockUp;

public class HelperUtilsTest extends TestBase {
  private static final String GROUP_BUDGET_ENDPOINT = getEndpoint(FinanceStorageGroupBudgets.class);

  @Test
  @Disabled("disabled due to incompatibility of jdk17 + jmockit + jacoco"
    + "remove annotation and check with command: 'mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install' ")
  public void getEntitiesCollectionWithDistinctOnFailCqlExTest() {
    new MockUp<PgUtil>()
    {
      @Mock
      PostgresClient postgresClient(Context vertxContext, Map<String, String> okapiHeaders) {
        throw new CQLQueryValidationException(null);
      }
    };
    get(storageUrl(GROUP_BUDGET_ENDPOINT)).statusCode(HttpStatus.HTTP_BAD_REQUEST.toInt()).contentType(TEXT_PLAIN);
  }


  @Test
  @Disabled("Disabled until 'jdk11 + jmockit + jacoco' incompatibility will be fixed")
  public void entitiesMetadataHolderRespond400FailTest() {
    new MockUp<EntitiesMetadataHolder>()
    {
      @Mock
      Method getRespond400WithTextPlainMethod() throws NoSuchMethodException {
        throw new NoSuchMethodException();
      }
    };
    get(storageUrl(GROUP_BUDGET_ENDPOINT)).statusCode(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toInt()).contentType(TEXT_PLAIN);
  }

  @Test
  @Disabled("Disabled until 'jdk11 + jmockit + jacoco' incompatibility will be fixed")
  public void entitiesMetadataHolderRespond500FailTest() {
    new MockUp<EntitiesMetadataHolder>()
    {
      @Mock
      Method getRespond500WithTextPlainMethod() throws NoSuchMethodException {
        throw new NoSuchMethodException();
      }
    };
    get(storageUrl(GROUP_BUDGET_ENDPOINT)).statusCode(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toInt()).contentType(TEXT_PLAIN);
  }

  @Test
  @Disabled("Disabled until 'jdk11 + jmockit + jacoco' incompatibility will be fixed")
  public void entitiesMetadataHolderRespond200FailTest() {
    new MockUp<EntitiesMetadataHolder>()
    {
      @Mock
      Method getRespond200WithApplicationJson() throws NoSuchMethodException {
        throw new NoSuchMethodException();
      }
    };
    get(storageUrl(GROUP_BUDGET_ENDPOINT)).statusCode(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toInt()).contentType(TEXT_PLAIN);
  }

  @Test
  @Disabled("disabled due to incompatibility of jdk17 + jmockit + jacoco"
    + "remove annotation and check with command: 'mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install' ")
  public void getEntitiesCollectionWithDistinctOnFailNpExTest() {
    new MockUp<PgUtil>()
    {
      @Mock
      PostgresClient postgresClient(Context vertxContext, Map<String, String> okapiHeaders) {
        throw new NullPointerException();
      }
    };
    get(storageUrl(GROUP_BUDGET_ENDPOINT)).statusCode(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toInt())
      .contentType(TEXT_PLAIN);
  }

  @Test
  public void testSQLUniqueConstraintFound() {
    String constraintName = HelperUtils.getSQLUniqueConstraintName("unique constraint \"idx_name_code\"");
    Assertions.assertEquals("idx_name_code", constraintName);
  }

  @Test
  public void testSQLUniqueConstraintNotFound() {
    String constraintName = HelperUtils.getSQLUniqueConstraintName("error \"error\"");
    Assertions.assertEquals(StringUtils.EMPTY, constraintName);
  }

  private ValidatableResponse get(URL endpoint) {
    return RestAssured
      .with()
      .header(TENANT_HEADER)
      .get(endpoint)
      .then();
  }
}
