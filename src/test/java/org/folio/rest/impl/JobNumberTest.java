package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.StorageTestSuite.storageUrl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.restassured.http.ContentType;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.folio.HttpStatus;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

public class JobNumberTest extends TestBase {

  private static List<Long> jobNumberList;

  private static final String SEQUENCE_NUMBER = "sequenceNumber";
  private static final String JOB_NUMBER_ENDPOINT = "/finance-storage/job-number";
  private static final String DROP_SEQUENCE_QUERY = "DROP SEQUENCE diku_mod_finance_storage.job_number";

  @BeforeAll
  public static void setUp() {
    jobNumberList  = new ArrayList<>();
  }

  @RepeatedTest(3)
  public void testGetJobNumber() {
    assertTrue(jobNumberList.add(getNumberAsLong()));
  }

  @AfterAll
  public static void tearDown() throws Exception {

    // Positive scenario - testing of number increase
    for(int i = 0; i < jobNumberList.size(); i++) {
      assertThat(jobNumberList.get(i) - jobNumberList.get(0), equalTo((long) i));
    }

    // Negative scenario - retrieving number from non-existed sequence
    dropSequenceInDb();
    testProcessingErrorReply();
  }

  private long getNumberAsLong() {
    return Long.parseLong(getData(JOB_NUMBER_ENDPOINT)
      .then()
      .statusCode(HttpStatus.HTTP_OK.toInt())
      .extract()
      .response()
      .path(SEQUENCE_NUMBER));
  }

  private static void dropSequenceInDb() throws Exception {
    CompletableFuture<RowSet<Row>> future = new CompletableFuture<>();
    PostgresClient.getInstance(Vertx.vertx()).execute(DROP_SEQUENCE_QUERY, result -> {
      if(result.failed()) {
        future.completeExceptionally(result.cause());
      } else {
        future.complete(result.result());
      }
    });
    future.get(10, TimeUnit.SECONDS);
  }

  private static void testProcessingErrorReply() {
    given()
      .header(TENANT_HEADER)
      .contentType(ContentType.JSON)
      .get(storageUrl(JOB_NUMBER_ENDPOINT))
      .then()
      .statusCode(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toInt())
      .contentType(TEXT_PLAIN)
      .extract()
      .response();
  }
}
