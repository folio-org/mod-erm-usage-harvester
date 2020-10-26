package org.olf.erm.usage.harvester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.CounterReports;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestVia;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestingStatus;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.jaxrs.model.UsageDataProviders;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class WorkerVerticleIT {

  private final Vertx vertx = Vertx.vertx();
  private final List<String> tenants = List.of("tenanta", "tenantb");
  private final JsonArray tenantsJsonArray =
      tenants.stream()
          .map(s -> new JsonObject().put("id", s))
          .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
  private final String harvesterPath = "/erm-usage-harvester";
  private final String reportsPath = "/counter-reports";
  private final String providerPath = "/usage-data-providers";
  private final String aggregatorPath = "/aggregator-settings";
  private final String modConfigurationPath = "/configurations/entries";
  private final String tenantsPath = "/_/proxy/tenants";
  private final Pattern pattern =
      Pattern.compile("/usage-data-providers/(.{8}-.{4}-.{4}-.{4}-.{12}).*");
  private final Map<String, List<UsageDataProvider>> tenantUDPMap = new HashMap<>();

  @Rule
  public WireMockRule baseRule =
      new WireMockRule(wireMockConfig().extensions(new UDPResponseTransformer()).dynamicPort());

  @Rule
  public WireMockRule serviceProviderARule =
      new WireMockRule(
          wireMockConfig().extensions(new ServiceProviderResponseTransformer()).dynamicPort());

  @Rule
  public WireMockRule serviceProviderBRule =
      new WireMockRule(
          wireMockConfig().extensions(new ServiceProviderResponseTransformer()).dynamicPort());

  @Rule
  public EmbeddedPostgresRule embeddedPostgresRule =
      new EmbeddedPostgresRule(vertx, tenants.toArray(String[]::new));

  private String okapiUrl;

  private void createUDPs() {
    UsageDataProvider udp1 =
        new UsageDataProvider()
            .withId("dcb0eec3-f63c-440b-adcd-acca2ec44f39")
            .withLabel("Provider A")
            .withHarvestingConfig(
                new HarvestingConfig()
                    .withRequestedReports(List.of("JR1"))
                    .withReportRelease(4)
                    .withHarvestingStart("2018-01")
                    .withHarvestingEnd("2020-04")
                    .withHarvestingStatus(HarvestingStatus.ACTIVE)
                    .withHarvestVia(HarvestVia.SUSHI)
                    .withSushiConfig(
                        new SushiConfig()
                            .withServiceType("wvitp")
                            .withServiceUrl(serviceProviderARule.baseUrl())))
            .withSushiCredentials(
                new SushiCredentials()
                    .withApiKey("apiKey")
                    .withCustomerId("custId")
                    .withRequestorId("reqId"));
    UsageDataProvider udp2 =
        new UsageDataProvider()
            .withId("0e8ac1f3-dd68-45a1-9edc-c2c2c3699214")
            .withLabel("Provider B")
            .withHarvestingConfig(
                new HarvestingConfig()
                    .withRequestedReports(List.of("JR1"))
                    .withReportRelease(4)
                    .withHarvestingStart("2018-01")
                    .withHarvestingEnd("2020-04")
                    .withHarvestingStatus(HarvestingStatus.ACTIVE)
                    .withHarvestVia(HarvestVia.SUSHI)
                    .withSushiConfig(
                        new SushiConfig()
                            .withServiceType("wvitp")
                            .withServiceUrl(serviceProviderBRule.baseUrl())))
            .withSushiCredentials(
                new SushiCredentials()
                    .withApiKey("apiKey")
                    .withCustomerId("custId")
                    .withRequestorId("reqId"));
    tenantUDPMap.put("tenanta", List.of(udp1, udp2));
  }

  @Before
  public void setup(TestContext context) {
    createUDPs();

    okapiUrl = baseRule.baseUrl();
    int httpPort = NetworkUtils.nextFreePort();
    JsonObject cfg =
        new JsonObject()
            .put("okapiUrl", okapiUrl)
            .put("reportsPath", reportsPath)
            .put("providerPath", providerPath)
            .put("aggregatorPath", aggregatorPath)
            .put("modConfigurationPath", modConfigurationPath)
            .put("tenantsPath", tenantsPath)
            .put("http.port", httpPort);

    baseRule.stubFor(
        get(urlMatching(harvesterPath + "/.*"))
            .willReturn(aResponse().proxiedFrom("http://localhost:" + httpPort)));

    baseRule.stubFor(
        get(urlPathEqualTo(modConfigurationPath))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    baseRule.stubFor(
        get(urlPathEqualTo(tenantsPath))
            .willReturn(aResponse().withStatus(200).withBody(tenantsJsonArray.encodePrettily())));

    baseRule.stubFor(
        get(urlPathEqualTo(providerPath))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        Json.encodePrettily(
                            new UsageDataProviders()
                                .withUsageDataProviders(tenantUDPMap.get("tenanta"))
                                .withTotalRecords(tenantUDPMap.get("tenanta").size())))));

    baseRule.stubFor(
        get(urlMatching(providerPath + "/.*"))
            .willReturn(aResponse().withTransformers("UDPResponseTransformer")));

    baseRule.stubFor(
        put(urlMatching(providerPath + "/.*")).willReturn(aResponse().withStatus(204)));

    baseRule.stubFor(
        get(urlPathEqualTo(reportsPath))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        Json.encodePrettily(
                            new CounterReports()
                                .withCounterReports(Collections.emptyList())
                                .withTotalRecords(0)))));

    baseRule.stubFor(post(urlPathEqualTo(reportsPath)).willReturn(aResponse().withStatus(201)));

    serviceProviderARule.stubFor(
        get(anyUrl())
            .willReturn(aResponse().withTransformers("ServiceProviderResponseTransformer")));

    serviceProviderBRule.stubFor(
        get(anyUrl())
            .willReturn(aResponse().withTransformers("ServiceProviderResponseTransformer")));

    vertx.deployVerticle(
        RestVerticle.class.getName(),
        new DeploymentOptions().setConfig(cfg),
        context.asyncAssertSuccess());
  }

  @Test
  public void testNumberOfRequestsMade(TestContext context) {
    Async async = context.async();

    Token token = new Token(Token.createFakeJWTForTenant("tenanta"));
    ValidatableResponse then =
        given()
            .headers(
                XOkapiHeaders.TENANT, token.getTenantId(), XOkapiHeaders.TOKEN, token.getToken())
            .get(okapiUrl + "/erm-usage-harvester/start")
            .then();
    System.out.println(
        then.extract().statusCode()
            + then.extract().statusLine()
            + then.extract().body().asString());
    then.statusCode(200);

    vertx.setPeriodic(
        1000,
        id -> {
          if (vertx.deploymentIDs().size() <= 1) {
            serviceProviderARule.verify(28, getRequestedFor(urlPathEqualTo("/")));
            serviceProviderARule.verify(
                1,
                getRequestedFor(urlPathEqualTo("/"))
                    .withQueryParam("report", equalTo("JR1"))
                    .withQueryParam("begin", equalTo("2020-01-01"))
                    .withQueryParam("end", equalTo("2020-01-31")));
            serviceProviderBRule.verify(28, getRequestedFor(urlPathEqualTo("/")));
            serviceProviderBRule.verify(
                1,
                getRequestedFor(urlPathEqualTo("/"))
                    .withQueryParam("report", equalTo("JR1"))
                    .withQueryParam("begin", equalTo("2020-01-01"))
                    .withQueryParam("end", equalTo("2020-01-31")));
            baseRule.verify(28 * 2, postRequestedFor(urlEqualTo(reportsPath)));
            baseRule.verify(2, putRequestedFor(urlMatching(providerPath + "/.*")));
            vertx.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  static class ServiceProviderResponseTransformer extends ResponseDefinitionTransformer {

    @Override
    public ResponseDefinition transform(
        Request request,
        ResponseDefinition responseDefinition,
        FileSource files,
        Parameters parameters) {

      return new ResponseDefinitionBuilder()
          .withStatus(200)
          .withBody(Json.encodePrettily(new Report()))
          .build();
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }

    @Override
    public String getName() {
      return "ServiceProviderResponseTransformer";
    }
  }

  class UDPResponseTransformer extends ResponseDefinitionTransformer {

    @Override
    public ResponseDefinition transform(
        Request request,
        ResponseDefinition responseDefinition,
        FileSource files,
        Parameters parameters) {

      String tenant = request.getHeader(XOkapiHeaders.TENANT);
      Matcher matcher = pattern.matcher(request.getUrl());
      String uuid = (matcher.matches()) ? matcher.group(1) : "";

      return Optional.ofNullable(tenantUDPMap.get(tenant))
          .flatMap(
              list ->
                  list.stream()
                      .filter(udp -> udp.getId().equals(uuid))
                      .findFirst()
                      .map(Json::encodePrettily))
          .map(s -> new ResponseDefinitionBuilder().withStatus(200).withBody(s).build())
          .orElse(new ResponseDefinitionBuilder().withStatus(404).build());
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }

    @Override
    public String getName() {
      return "UDPResponseTransformer";
    }
  }
}