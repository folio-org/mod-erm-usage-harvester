package org.olf.erm.usage.harvester.rest.impl;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.parsing.Parser;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.ErrorHandling;
import org.folio.rest.jaxrs.model.ReportQueuedForProcessing;
import org.folio.rest.jaxrs.model.ServiceBusy;
import org.folio.rest.jaxrs.model.ServiceNotAvailable;
import org.folio.rest.jaxrs.model.TooManyRequests;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.olf.erm.usage.harvester.PostgresContainerRule;

// testZDatabaseError stops Postgres and therefore has to run last
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(VertxUnitRunner.class)
public class ErmUsageHarvesterErrorHandlingAPIIT {

  private static final String TENANT = "testtenant";
  private static final String TABLE_NAME = "error_handling";
  private static final Vertx vertx = Vertx.vertx();
  private static final RequestSpecification baseReq =
      new RequestSpecBuilder()
          .addHeader(XOkapiHeaders.TENANT, TENANT)
          .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
          .build();
  private static final ErrorHandling errorHandling =
      new ErrorHandling()
          .withNumberOfFailedAttempts(3)
          .withServiceNotAvailable(new ServiceNotAvailable().withEnabled(true).withWaitTime(10))
          .withServiceBusy(new ServiceBusy().withEnabled(true).withWaitTime(20))
          .withReportQueuedForProcessing(
              new ReportQueuedForProcessing().withEnabled(false).withWaitTime(30))
          .withTooManyRequests(
              new TooManyRequests().withEnabled(true).withNumberOfRequests(2).withTimeframe(5));
  private static final ErrorHandling errorHandling2 =
      new ErrorHandling()
          .withNumberOfFailedAttempts(4)
          .withServiceNotAvailable(new ServiceNotAvailable().withEnabled(false).withWaitTime(11))
          .withServiceBusy(new ServiceBusy().withEnabled(false).withWaitTime(21))
          .withReportQueuedForProcessing(
              new ReportQueuedForProcessing().withEnabled(true).withWaitTime(31))
          .withTooManyRequests(
              new TooManyRequests().withEnabled(false).withNumberOfRequests(3).withTimeframe(6));

  @ClassRule public static PostgresContainerRule pgRule = new PostgresContainerRule(vertx, TENANT);

  @BeforeClass
  public static void beforeClass(TestContext context) {
    int port = NetworkUtils.nextFreePort();

    RestAssured.reset();
    RestAssured.port = port;
    RestAssured.basePath = "erm-usage-harvester/error-handling";
    RestAssured.defaultParser = Parser.JSON;

    DeploymentOptions options = new DeploymentOptions();
    options.setConfig(new JsonObject().put("http.port", port).put("testing", true));
    vertx
        .deployVerticle(RestVerticle.class.getName(), options)
        .onComplete(context.asyncAssertSuccess());
  }

  @AfterClass
  public static void afterClass() {
    vertx.close();
    RestAssured.reset();
  }

  @Before
  public void before(TestContext context) {
    PostgresClient.getInstance(vertx, TENANT)
        .delete(TABLE_NAME, new Criterion(new Criteria()))
        .onComplete(context.asyncAssertSuccess());
  }

  private static ErrorHandling getErrorHandling() {
    return given()
        .spec(baseReq)
        .get()
        .then()
        .statusCode(200)
        .extract()
        .as(ErrorHandling.class)
        .withId(null);
  }

  private static void postErrorHandling(ErrorHandling eh) {
    given().spec(baseReq).body(eh).post().then().statusCode(204);
  }

  @Test
  public void testGetDefaults() {
    assertThat(getErrorHandling())
        .usingRecursiveComparison()
        .isEqualTo(
            new ErrorHandling()
                .withServiceNotAvailable(new ServiceNotAvailable())
                .withServiceBusy(new ServiceBusy())
                .withReportQueuedForProcessing(new ReportQueuedForProcessing())
                .withTooManyRequests(new TooManyRequests()));
  }

  @Test
  public void testPostAndUpdate() {
    postErrorHandling(errorHandling);
    assertThat(getErrorHandling()).usingRecursiveComparison().isEqualTo(errorHandling);

    postErrorHandling(errorHandling2);
    assertThat(getErrorHandling()).usingRecursiveComparison().isEqualTo(errorHandling2);
  }

  @Test
  public void testValidation() {
    given()
        .spec(baseReq)
        .body(new ErrorHandling().withServiceBusy(new ServiceBusy().withWaitTime(0)))
        .post()
        .then()
        .statusCode(422);
    given().spec(baseReq).body("{\"foo\": 1}").post().then().statusCode(422);
  }

  @Test
  public void testZDatabaseError() {
    PostgresClient.stopPostgresTester();
    given().spec(baseReq).body(errorHandling).post().then().statusCode(500);
    given().spec(baseReq).get().then().statusCode(500);
  }
}
