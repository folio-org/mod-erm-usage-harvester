package org.olf.erm.usage.harvester;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class APITest {

  private static Vertx vertx;

  private static final HarvesterVerticle harvester = new HarvesterVerticle();

  private static final String deployCfg = "{\n" + "  \"okapiUrl\": \"http://localhost:9130\",\n"
      + "  \"tenantsPath\": \"/_/proxy/tenants\",\n" + "  \"reportsPath\": \"/counter-reports\",\n"
      + "  \"providerPath\": \"/usage-data-providers\",\n"
      + "  \"aggregatorPath\": \"/aggregator-settings\",\n" + "  \"moduleIds\": [\n"
      + "    \"mod-erm-usage-0.2.0-SNAPSHOT\",\n"
      + "    \"mod-erm-usage-harvester-0.2.0-SNAPSHOT\"\n" + "  ],\n"
      + "  \"loginPath\": \"/bl-users/login\",\n" + "  \"requiredPerm\": \"ermusage.all\"\n" + "}\n"
      + "";

  @BeforeClass
  public static void setup(TestContext context) {
    vertx = Vertx.vertx();

    JsonObject cfg = new JsonObject(deployCfg);
    cfg.put("testing", true);
    cfg.put("http.port", 8082);
    RestAssured.port = 8082;
    vertx.deployVerticle(harvester, new DeploymentOptions().setConfig(cfg),
        context.asyncAssertSuccess());
  }

  @AfterClass
  public static void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
    RestAssured.reset();
  }

  @Test
  public void startHarvester403() {
    when().get("/harvester/start").then().statusCode(403);
  }

  @Test
  public void startHarvester200() {
    given().header(new Header(XOkapiHeaders.TENANT, "testtenant"))
        .when()
        .get("/harvester/start")
        .then()
        .statusCode(200)
        .body("message", containsString("testtenant"));
  }

  @Test
  public void startProvider403() {
    when().get("/harvester/start/5b8ab2bd-e470-409c-9a6c-845d979da05e").then().statusCode(403);
  }

  @Test
  public void startProvider200() {
    given().header(new Header(XOkapiHeaders.TENANT, "testtenant"))
        .when()
        .get("/harvester/start/5b8ab2bd-e470-409c-9a6c-845d979da05e")
        .then()
        .statusCode(200)
        .body("message", allOf(containsString("testtenant"),
            containsString("5b8ab2bd-e470-409c-9a6c-845d979da05e")));
  }

  @Test
  public void getImplementations() {
    when().get("/harvester/impl")
        .then()
        .statusCode(200)
        .body("implementations.size()", greaterThanOrEqualTo(2))
        .body("implementations.type", hasItems("cs41", "NSS"));
  }

  @Test
  public void getImplementationsAggregator() {
    when().get("/harvester/impl?aggregator=true")
        .then()
        .statusCode(200)
        .body("implementations.size()", greaterThanOrEqualTo(1))
        .body("implementations.type", hasItem("NSS"))
        .body("implementations.isAggregator", everyItem(is(true)));
  }

  @Test
  public void getImplementationsNonAggregator() {
    when().get("/harvester/impl?aggregator=false")
        .then()
        .statusCode(200)
        .body("implementations.size()", greaterThanOrEqualTo(1))
        .body("implementations.type", hasItem("cs41"))
        .body("implementations.isAggregator", everyItem(is(false)));
  }
}
