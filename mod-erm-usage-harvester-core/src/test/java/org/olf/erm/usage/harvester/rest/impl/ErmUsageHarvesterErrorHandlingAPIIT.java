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
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.PostgresContainerRule;

@RunWith(VertxUnitRunner.class)
public class ErmUsageHarvesterErrorHandlingAPIIT {

  private static final String TENANT = "testtenant";
  private static final Vertx vertx = Vertx.vertx();

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

  @Test
  public void testGetPostAndValidation() {
    RequestSpecification baseReq =
        new RequestSpecBuilder()
            .addHeader(XOkapiHeaders.TENANT, TENANT)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .build();
    ErrorHandling errorHandling =
        new ErrorHandling()
            .withNumberOfFailedAttempts(3)
            .withServiceNotAvailable(new ServiceNotAvailable().withEnabled(true).withWaitTime(10))
            .withServiceBusy(new ServiceBusy().withEnabled(true).withWaitTime(20))
            .withReportQueuedForProcessing(
                new ReportQueuedForProcessing().withEnabled(false).withWaitTime(30))
            .withTooManyRequests(
                new TooManyRequests().withEnabled(true).withNumberOfRequests(2).withTimeframe(5));
    ErrorHandling errorHandling2 =
        new ErrorHandling()
            .withNumberOfFailedAttempts(4)
            .withServiceNotAvailable(new ServiceNotAvailable().withEnabled(false).withWaitTime(11))
            .withServiceBusy(new ServiceBusy().withEnabled(false).withWaitTime(21))
            .withReportQueuedForProcessing(
                new ReportQueuedForProcessing().withEnabled(true).withWaitTime(31))
            .withTooManyRequests(
                new TooManyRequests().withEnabled(false).withNumberOfRequests(3).withTimeframe(6));

    // nothing saved yet: defaults
    assertThat(
            given()
                .spec(baseReq)
                .get()
                .then()
                .statusCode(200)
                .extract()
                .as(ErrorHandling.class)
                .withId(null))
        .usingRecursiveComparison()
        .isEqualTo(
            new ErrorHandling()
                .withServiceNotAvailable(new ServiceNotAvailable())
                .withServiceBusy(new ServiceBusy())
                .withReportQueuedForProcessing(new ReportQueuedForProcessing())
                .withTooManyRequests(new TooManyRequests()));

    // POST
    given().spec(baseReq).body(errorHandling).post().then().statusCode(204);
    assertThat(
            given()
                .spec(baseReq)
                .get()
                .then()
                .statusCode(200)
                .extract()
                .as(ErrorHandling.class)
                .withId(null))
        .usingRecursiveComparison()
        .isEqualTo(errorHandling);

    // UPDATE
    given().spec(baseReq).body(errorHandling2).post().then().statusCode(204);
    assertThat(
            given()
                .spec(baseReq)
                .get()
                .then()
                .statusCode(200)
                .extract()
                .as(ErrorHandling.class)
                .withId(null))
        .usingRecursiveComparison()
        .isEqualTo(errorHandling2);

    // validation
    given()
        .spec(baseReq)
        .body(new ErrorHandling().withServiceBusy(new ServiceBusy().withWaitTime(0)))
        .post()
        .then()
        .statusCode(422);
    given().spec(baseReq).body("{\"foo\": 1}").post().then().statusCode(422);

    PostgresClient.stopPostgresTester();
    given().spec(baseReq).body(errorHandling).post().then().statusCode(500);
    given().spec(baseReq).get().then().statusCode(500);
  }
}
