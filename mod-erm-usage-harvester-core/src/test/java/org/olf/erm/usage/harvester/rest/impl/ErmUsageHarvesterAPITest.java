package org.olf.erm.usage.harvester.rest.impl;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.folio.rest.impl.ErmUsageHarvesterAPI.MESSAGE_NO_TOKEN;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.parsing.Parser;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.Map;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.impl.StdSchedulerFactory;

@RunWith(VertxUnitRunner.class)
public class ErmUsageHarvesterAPITest {

  private static final String TENANT_ERR_MSG = "Tenant must be set";
  private static final String TENANT = "testtenant";
  private static final Map<String, String> OKAPI_HEADERS =
      Map.of(XOkapiHeaders.TENANT, TENANT, XOkapiHeaders.TOKEN, "someToken");

  private static Vertx vertx;

  @BeforeClass
  public static void beforeClass(TestContext context) {
    vertx = Vertx.vertx();

    int port = NetworkUtils.nextFreePort();
    JsonObject cfg = new JsonObject();
    cfg.put("testing", true);
    cfg.put("http.port", port);
    RestAssured.reset();
    RestAssured.port = port;
    RestAssured.basePath = "erm-usage-harvester";
    RestAssured.defaultParser = Parser.JSON;
    vertx.deployVerticle(
        "org.folio.rest.RestVerticle",
        new DeploymentOptions().setConfig(cfg),
        context.asyncAssertSuccess());
  }

  @Before
  public void before() throws Exception {
    StdSchedulerFactory.getDefaultScheduler().clear();
  }

  @AfterClass
  public static void afterClass(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
    RestAssured.reset();
  }

  @Test
  public void startHarvesterNoTenant() {
    given().get("/start").then().statusCode(400).body(containsString(TENANT_ERR_MSG));
  }

  @Test
  public void startHarvesterNoToken() {
    given()
        .header(new Header(XOkapiHeaders.TENANT, TENANT))
        .when()
        .get("/start")
        .then()
        .statusCode(500)
        .body(equalTo(MESSAGE_NO_TOKEN));
  }

  @Test
  public void startHarvester200() {
    given()
        .headers(OKAPI_HEADERS)
        .when()
        .get("/start")
        .then()
        .log()
        .all()
        .statusCode(200)
        .body("message", containsString(TENANT));
  }

  @Test
  public void startProviderNoTenant() {
    when()
        .get("/start/5b8ab2bd-e470-409c-9a6c-845d979da05e")
        .then()
        .statusCode(400)
        .body(containsString(TENANT_ERR_MSG));
  }

  @Test
  public void startProviderNoToken() {
    given()
        .header(new Header(XOkapiHeaders.TENANT, TENANT))
        .when()
        .get("/start")
        .then()
        .statusCode(500)
        .body(equalTo(MESSAGE_NO_TOKEN));
  }

  @Test
  public void startProvider200() {
    given()
        .headers(OKAPI_HEADERS)
        .when()
        .get("/start/5b8ab2bd-e470-409c-9a6c-845d979da05e")
        .then()
        .statusCode(200)
        .body(
            "message",
            allOf(containsString(TENANT), containsString("5b8ab2bd-e470-409c-9a6c-845d979da05e")));
  }

  @Test
  public void getImplementations() {
    given()
        .header(new Header(XOkapiHeaders.TENANT, TENANT))
        .when()
        .get("/impl")
        .then()
        .statusCode(200)
        .body("implementations.size()", greaterThanOrEqualTo(2))
        .body("implementations.type", hasItems("test1", "test2"));
  }

  @Test
  public void getImplementationsAggregator() {
    given()
        .header(new Header(XOkapiHeaders.TENANT, TENANT))
        .when()
        .get("/impl?aggregator=true")
        .then()
        .statusCode(200)
        .body("implementations.size()", greaterThanOrEqualTo(1))
        .body("implementations.type", hasItem("test2"))
        .body("implementations.isAggregator", everyItem(is(true)));
  }

  @Test
  public void getImplementationsNonAggregator() {
    given()
        .header(new Header(XOkapiHeaders.TENANT, TENANT))
        .when()
        .get("/impl?aggregator=false")
        .then()
        .statusCode(200)
        .body("implementations.size()", greaterThanOrEqualTo(1))
        .body("implementations.type", hasItem("test1"))
        .body("implementations.isAggregator", everyItem(is(false)));
  }
}
