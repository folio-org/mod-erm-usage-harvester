package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.parsing.Parser;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.stream.Stream;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.OkapiClient;

@RunWith(VertxUnitRunner.class)
public class StartAPITest {

  private static Vertx vertx = Vertx.vertx();

  @ClassRule
  public static WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

  private static final String deployCfg =
      "{\n"
          + "  \"okapiUrl\": \"http://localhost:9130\",\n"
          + "  \"tenantsPath\": \"/_/proxy/tenants\",\n"
          + "  \"reportsPath\": \"/counter-reports\",\n"
          + "  \"providerPath\": \"/usage-data-providers\",\n"
          + "  \"aggregatorPath\": \"/aggregator-settings\"\n"
          + "}\n"
          + "";

  @BeforeClass
  public static void setup(TestContext context) {
    vertx = Vertx.vertx();

    int port = NetworkUtils.nextFreePort();
    JsonObject cfg = new JsonObject(deployCfg);
    cfg.put("testing", true);
    cfg.put("http.port", port);
    String okapiMockUrl = "http://localhost:" + wireMockRule.port() + "/okapiMock";
    cfg.put("okapiUrl", okapiMockUrl);
    RestAssured.port = port;
    RestAssured.defaultParser = Parser.JSON;
    RestAssured.filters(new ResponseLoggingFilter(), new RequestLoggingFilter());
    vertx.deployVerticle(
        "org.folio.rest.RestVerticle",
        new DeploymentOptions().setConfig(cfg),
        context.asyncAssertSuccess());
  }

  @AfterClass
  public static void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
    RestAssured.reset();
  }

  @Test
  public void testThatAllTenantsAreStarted(TestContext context) {
    JsonArray enabled =
        new JsonArray()
            .add(
                new JsonObject()
                    .put("id", OkapiClient.INTERFACE_NAME)
                    .put("version", OkapiClient.INTERFACE_VER));
    JsonArray jsonArray =
        Stream.iterate(1, i -> ++i)
            .limit(3)
            .map(
                i -> {
                  stubFor(
                      get("/okapiMock/_/proxy/tenants/tenant" + i + "/interfaces")
                          .willReturn(
                              aResponse()
                                  .withStatus(200)
                                  .withBody(
                                      (i % 2 != 0)
                                          ? enabled.encodePrettily()
                                          : new JsonArray().encodePrettily())));
                  return new JsonObject()
                      .put("id", "tenant" + i)
                      .put("name", "Library" + i)
                      .put("description", "Description" + i);
                })
            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);

    stubFor(
        get("/okapiMock/_/proxy/tenants")
            .willReturn(aResponse().withStatus(200).withBody(jsonArray.encodePrettily())));
    stubFor(
        get("/okapiMock/erm-usage-harvester/start")
            .willReturn(aResponse().withStatus(200).withBody(jsonArray.encodePrettily())));

    given()
        .header(XOkapiHeaders.TENANT, "someTenant")
        .get("/_/start")
        .then()
        .statusCode(200)
        .body(containsString("Processing"), containsString("requested"));

    // wait for requests to be made
    Async async = context.async();
    vertx.setTimer(2500, ar -> async.complete());
    async.awaitSuccess();

    verify(getRequestedFor(urlMatching("/okapiMock/_/proxy/tenants")));
    verify(getRequestedFor(urlMatching("/okapiMock/_/proxy/tenants/tenant1/interfaces")));
    verify(getRequestedFor(urlMatching("/okapiMock/_/proxy/tenants/tenant2/interfaces")));
    verify(getRequestedFor(urlMatching("/okapiMock/_/proxy/tenants/tenant3/interfaces")));

    verify(
        getRequestedFor(urlMatching("/okapiMock/erm-usage-harvester/start"))
            .withHeader(XOkapiHeaders.TENANT, equalTo("tenant1")));
    verify(
        exactly(0),
        getRequestedFor(urlMatching("/okapiMock/erm-usage-harvester/start"))
            .withHeader(XOkapiHeaders.TENANT, equalTo("tenant2")));
    verify(
        getRequestedFor(urlMatching("/okapiMock/erm-usage-harvester/start"))
            .withHeader(XOkapiHeaders.TENANT, equalTo("tenant3")));
  }

  @Test
  public void testNoTenants() {
    stubFor(get("/okapiMock/_/proxy/tenants").willReturn(aResponse().withStatus(404)));
    given().header(XOkapiHeaders.TENANT, "someTenant").get("/_/start").then().statusCode(200);
  }

  @Test
  public void testNoTenantHeader() {
    given().get("/_/start").then().statusCode(400);
  }
}
