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
import org.folio.rest.jaxrs.model.RateOption;
import org.folio.rest.jaxrs.model.WaitOption;
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
import org.olf.erm.usage.harvester.errorhandling.ErrorHandlingDefaults;

// testZDatabaseError stops Postgres and therefore has to run last
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(VertxUnitRunner.class)
public class ErmUsageHarvesterErrorHandlingAPIIT {

  private static final String TENANT = "testtenant";
  private static final String TABLE_NAME = "error_handling";
  private static final Vertx vertx = Vertx.vertx();
  private static RequestSpecification baseReq;
  private static final ErrorHandling errorHandling =
      new ErrorHandling()
          .withMaxFailedAttempts(3)
          .withCode1000(new WaitOption().withWaitMinutes(10))
          .withCode1010(new WaitOption().withWaitMinutes(20))
          .withCode1011(new WaitOption().withWaitMinutes(30))
          .withCode1020(new RateOption().withNumberOfRequests(2).withTimeframeSeconds(5));
  // options that are not set have to stay unset after an update
  private static final ErrorHandling errorHandling2 =
      new ErrorHandling()
          .withCode1011(new WaitOption().withWaitMinutes(31))
          .withCode1020(new RateOption().withNumberOfRequests(3).withTimeframeSeconds(6));

  @ClassRule public static PostgresContainerRule pgRule = new PostgresContainerRule(vertx, TENANT);

  @BeforeClass
  public static void beforeClass(TestContext context) {
    int port = NetworkUtils.nextFreePort();

    RestAssured.reset();
    RestAssured.port = port;
    RestAssured.basePath = "erm-usage-harvester/error-handling";
    RestAssured.defaultParser = Parser.JSON;
    // built after the RestAssured settings above, because the spec copies them on creation
    baseReq =
        new RequestSpecBuilder()
            .addHeader(XOkapiHeaders.TENANT, TENANT)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
            .build();

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
    return given().spec(baseReq).get().then().statusCode(200).extract().as(ErrorHandling.class);
  }

  private static void postErrorHandling(ErrorHandling eh) {
    given().spec(baseReq).body(eh).post().then().statusCode(204);
  }

  private static ErrorHandling getErrorHandlingDefaults() {
    return given()
        .spec(baseReq)
        .get("/defaults")
        .then()
        .statusCode(200)
        .extract()
        .as(ErrorHandling.class);
  }

  @Test
  public void testGetNothingSaved() {
    assertThat(getErrorHandling()).usingRecursiveComparison().isEqualTo(new ErrorHandling());
  }

  @Test
  public void testGetDefaults() {
    assertThat(getErrorHandlingDefaults())
        .hasNoNullFieldsOrPropertiesExcept("id")
        .usingRecursiveComparison()
        .isEqualTo(ErrorHandlingDefaults.get());
  }

  @Test
  public void testPostAndUpdate() {
    postErrorHandling(errorHandling);
    assertThat(getErrorHandling())
        .as("... the id of the stored record is not returned")
        .hasFieldOrPropertyWithValue("id", null)
        .usingRecursiveComparison()
        .isEqualTo(errorHandling);

    postErrorHandling(errorHandling2);
    assertThat(getErrorHandling()).usingRecursiveComparison().isEqualTo(errorHandling2);
  }

  private static void assertPostStatus(String body, int statusCode) {
    given().spec(baseReq).body(body).post().then().statusCode(statusCode);
  }

  @Test
  public void testValidation() {
    assertPostStatus("{\"foo\": 1}", 422);
    assertPostStatus("{\"code1000\": {}}", 422);
    assertPostStatus("{\"code1020\": {\"numberOfRequests\": 2}}", 422);
    assertPostStatus("{\"code1000\": {\"enabled\": true}}", 422);
  }

  @Test
  public void testValidationLimits() {
    // maxFailedAttempts: 1 - 20
    assertPostStatus("{\"maxFailedAttempts\": 0}", 422);
    assertPostStatus("{\"maxFailedAttempts\": 1}", 204);
    assertPostStatus("{\"maxFailedAttempts\": 20}", 204);
    assertPostStatus("{\"maxFailedAttempts\": 21}", 422);

    // waitMinutes: 1 - 1440, for every code using a WaitOption
    for (String code : new String[] {"code1000", "code1010", "code1011"}) {
      String template = "{\"" + code + "\": {\"waitMinutes\": %d}}";
      assertPostStatus(String.format(template, 0), 422);
      assertPostStatus(String.format(template, 1), 204);
      assertPostStatus(String.format(template, 1440), 204);
      assertPostStatus(String.format(template, 1441), 422);
    }

    // numberOfRequests: minimum 1, timeframeSeconds: 1 - 86400
    String rateTemplate = "{\"code1020\": {\"numberOfRequests\": %d, \"timeframeSeconds\": %d}}";
    assertPostStatus(String.format(rateTemplate, 0, 5), 422);
    assertPostStatus(String.format(rateTemplate, 1, 5), 204);
    assertPostStatus(String.format(rateTemplate, 2, 0), 422);
    assertPostStatus(String.format(rateTemplate, 2, 1), 204);
    assertPostStatus(String.format(rateTemplate, 2, 86400), 204);
    assertPostStatus(String.format(rateTemplate, 2, 86401), 422);
  }

  @Test
  public void testZDatabaseError() {
    PostgresClient.stopPostgresTester();
    given().spec(baseReq).body(errorHandling).post().then().statusCode(500);
    given().spec(baseReq).get().then().statusCode(500);
    // the defaults don't need the database
    assertThat(getErrorHandlingDefaults())
        .usingRecursiveComparison()
        .isEqualTo(ErrorHandlingDefaults.get());
  }
}
