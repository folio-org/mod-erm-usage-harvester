package org.olf.erm.usage.harvester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.io.Resources;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.endpoints.WorkerVerticleITProvider3;
import org.slf4j.LoggerFactory;

@RunWith(VertxUnitRunner.class)
public class WorkerVerticleIT {

  private static final Vertx vertx = Vertx.vertx();
  private static final String TENANTA = "tenanta";
  private static final Map<String, String> OKAPI_HEADERS =
      Map.of(XOkapiHeaders.TENANT, TENANTA, XOkapiHeaders.TOKEN, "someToken");
  private static final List<String> tenants = List.of(TENANTA, "tenantb");
  private static final String HARVESTER_PATH = "/erm-usage-harvester";
  private static final String HARVESTER_START_PATH = "/erm-usage-harvester/start";
  private static final Map<String, List<UsageDataProvider>> tenantUDPMap = new HashMap<>();

  private static String okapiUrl;
  private static String reportsPath;
  private static String providerPath;
  private static Pattern providerPathWithIdPattern;

  @ClassRule
  public static PostgresContainerRule pgContainerRule =
      new PostgresContainerRule(vertx, tenants.toArray(String[]::new));

  @ClassRule
  public static WireMockRule baseRule =
      new WireMockRule(wireMockConfig().extensions(new UDPResponseTransformer()).dynamicPort());

  @ClassRule
  public static WireMockRule serviceProviderARule =
      new WireMockRule(wireMockConfig().dynamicPort());

  @ClassRule
  public static WireMockRule serviceProviderBRule =
      new WireMockRule(wireMockConfig().dynamicPort());

  private void resetTenantUDPMap() {
    tenantUDPMap.clear();
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
    tenantUDPMap.put(TENANTA, new ArrayList<>(List.of(udp1, udp2)));
  }

  @BeforeClass
  public static void beforeClass(TestContext context) throws IOException {
    okapiUrl = baseRule.baseUrl();
    int httpPort = NetworkUtils.nextFreePort();

    String deployCfg =
        Resources.toString(Resources.getResource("config.json"), StandardCharsets.UTF_8);
    JsonObject cfg = new JsonObject(deployCfg).put("okapiUrl", okapiUrl).put("http.port", httpPort);
    String modConfigurationPath = cfg.getString("modConfigurationPath");
    String tenantsPath = cfg.getString("tenantsPath");
    providerPath = cfg.getString("providerPath");
    reportsPath = cfg.getString("reportsPath");
    providerPathWithIdPattern = Pattern.compile(providerPath + "/(.{8}-.{4}-.{4}-.{4}-.{12}).*");

    baseRule.stubFor(
        get(urlMatching(HARVESTER_PATH + "/.*"))
            .willReturn(aResponse().proxiedFrom("http://localhost:" + httpPort)));

    baseRule.stubFor(
        get(urlPathEqualTo(modConfigurationPath))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    JsonArray tenantsJsonArray =
        tenants.stream()
            .map(s -> new JsonObject().put("id", s))
            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
    baseRule.stubFor(
        get(urlPathEqualTo(tenantsPath))
            .willReturn(aResponse().withStatus(200).withBody(tenantsJsonArray.encodePrettily())));

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

    vertx.deployVerticle(
        RestVerticle.class.getName(),
        new DeploymentOptions().setConfig(cfg),
        context.asyncAssertSuccess());
  }

  @Before
  public void before() {
    resetTenantUDPMap();
    baseRule.resetRequests();
    serviceProviderARule.resetRequests();
    serviceProviderBRule.resetRequests();

    baseRule.stubFor(
        get(urlPathEqualTo(providerPath))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        Json.encodePrettily(
                            new UsageDataProviders()
                                .withUsageDataProviders(tenantUDPMap.get(TENANTA))
                                .withTotalRecords(tenantUDPMap.get(TENANTA).size())))));

    serviceProviderARule.stubFor(
        get(anyUrl())
            .willReturn(aResponse().withStatus(200).withBody(Json.encodePrettily(new Report()))));

    serviceProviderBRule.stubFor(
        get(anyUrl())
            .willReturn(aResponse().withStatus(200).withBody(Json.encodePrettily(new Report()))));
  }

  @Test
  public void testNumberOfRequestsMadeForTenant(TestContext context) {
    Async async = context.async();

    given().headers(OKAPI_HEADERS).get(okapiUrl + HARVESTER_START_PATH).then().statusCode(200);

    vertx.setPeriodic(
        1000,
        id -> {
          if (vertx.deploymentIDs().size() <= 1) {
            context.verify(
                v -> {
                  serviceProviderARule.verify(3, getRequestedFor(urlPathEqualTo("/")));
                  serviceProviderARule.verify(
                      1,
                      getRequestedFor(urlPathEqualTo("/"))
                          .withQueryParam("report", equalTo("JR1"))
                          .withQueryParam("begin", equalTo("2020-01-01"))
                          .withQueryParam("end", equalTo("2020-04-30")));
                  serviceProviderBRule.verify(3, getRequestedFor(urlPathEqualTo("/")));
                  serviceProviderBRule.verify(
                      1,
                      getRequestedFor(urlPathEqualTo("/"))
                          .withQueryParam("report", equalTo("JR1"))
                          .withQueryParam("begin", equalTo("2020-01-01"))
                          .withQueryParam("end", equalTo("2020-04-30")));
                  baseRule.verify(
                      2,
                      postRequestedFor(urlEqualTo(reportsPath))
                          .withRequestBody(matchingJsonPath("$.report.month", equalTo("2020-03"))));
                  baseRule.verify(28 * 2, postRequestedFor(urlEqualTo(reportsPath)));
                  baseRule.verify(2, putRequestedFor(urlMatching(providerPath + "/.*")));
                });
            vertx.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  @Test
  public void testNumberOfRequestsMadeForProviderWithErrors(TestContext context) {
    tenantUDPMap
        .get(TENANTA)
        .forEach(udp -> udp.getHarvestingConfig().getSushiConfig().setServiceType("wvitp2"));

    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    Async async = context.async();
    vertx.setPeriodic(
        1000,
        id -> {
          if (vertx.deploymentIDs().size() <= 1) {
            context.verify(
                v -> {
                  serviceProviderARule.verify(3 + 12, getRequestedFor(urlPathEqualTo("/")));
                  serviceProviderARule.verify(
                      1,
                      getRequestedFor(urlPathEqualTo("/"))
                          .withQueryParam("report", equalTo("JR1"))
                          .withQueryParam("begin", equalTo("2018-03-01"))
                          .withQueryParam("end", equalTo("2018-03-31")));
                  serviceProviderBRule.verify(0, getRequestedFor(urlPathEqualTo("/")));
                  baseRule.verify(28, postRequestedFor(urlEqualTo(reportsPath)));
                  baseRule.verify(
                      1,
                      postRequestedFor(urlEqualTo(reportsPath))
                          .withRequestBody(matchingJsonPath("$.report.month", equalTo("2020-03"))));
                  baseRule.verify(
                      1,
                      postRequestedFor(urlEqualTo(reportsPath))
                          .withRequestBody(matchingJsonPath("$.report", absent()))
                          .withRequestBody(
                              matchingJsonPath(
                                  "$.failedReason", matching("Report not valid:.*2018-03"))));
                  baseRule.verify(1, putRequestedFor(urlMatching(providerPath + "/.*")));
                });
            vertx.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  @Test
  public void testNumberOfThreadsUsedAfterTooManyRequestsError(TestContext context) {
    tenantUDPMap
        .get(TENANTA)
        .forEach(udp -> udp.getHarvestingConfig().getSushiConfig().setServiceType("wvitp3"));

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = loggerContext.getLogger(WorkerVerticleITProvider3.class);

    ListAppender<ILoggingEvent> newAppender = new ListAppender<>();
    logger.addAppender(newAppender);
    newAppender.start();

    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    Async async = context.async();
    vertx.setPeriodic(
        1000,
        id -> {
          if (vertx.deploymentIDs().size() <= 1) {
            context.verify(
                v -> {
                  serviceProviderARule.verify(
                      moreThanOrExactly(3 + 12), getRequestedFor(urlPathEqualTo("/")));
                  serviceProviderARule.verify(
                      1,
                      getRequestedFor(urlPathEqualTo("/"))
                          .withQueryParam("report", equalTo("JR1"))
                          .withQueryParam("begin", equalTo("2018-03-01"))
                          .withQueryParam("end", equalTo("2018-03-31")));
                  serviceProviderBRule.verify(0, getRequestedFor(urlPathEqualTo("/")));
                  baseRule.verify(28, postRequestedFor(urlEqualTo(reportsPath)));
                  baseRule.verify(1, putRequestedFor(urlMatching(providerPath + "/.*")));

                  List<String> threadNames =
                      newAppender.list.stream()
                          .filter(e -> e.getMessage().contains("Fetching report"))
                          .map(ILoggingEvent::getThreadName)
                          .collect(Collectors.toList());
                  assertThat(threadNames)
                      .hasSizeGreaterThanOrEqualTo(3 + 12)
                      .allMatch(s -> s.startsWith("pool"));

                  long first5ThreadCount = threadNames.subList(0, 5).stream().distinct().count();
                  assertThat(first5ThreadCount).isGreaterThanOrEqualTo(2);

                  long last5ThreadCount =
                      threadNames.subList(threadNames.size() - 5, threadNames.size()).stream()
                          .distinct()
                          .count();
                  assertThat(last5ThreadCount).isEqualTo(1);
                });
            vertx.cancelTimer(id);
            async.complete();
          }
        });

    async.await(30000);
  }

  @Test
  public void testNumberOfRequestsMadeForProviderConnectionError(TestContext context) {
    serviceProviderARule.stubFor(
        get(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    Async async = context.async();
    vertx.setPeriodic(
        1000,
        id -> {
          if (vertx.deploymentIDs().size() <= 1) {
            context.verify(
                v -> {
                  serviceProviderARule.verify(3, getRequestedFor(urlPathEqualTo("/")));
                  serviceProviderARule.verify(
                      1,
                      getRequestedFor(urlPathEqualTo("/"))
                          .withQueryParam("report", equalTo("JR1"))
                          .withQueryParam("begin", equalTo("2018-01-01"))
                          .withQueryParam("end", equalTo("2018-12-31")));
                  serviceProviderBRule.verify(0, getRequestedFor(urlPathEqualTo("/")));
                  baseRule.verify(28, postRequestedFor(urlEqualTo(reportsPath)));
                  baseRule.verify(
                      28,
                      postRequestedFor(urlEqualTo(reportsPath))
                          .withRequestBody(matchingJsonPath("$.report", absent()))
                          .withRequestBody(matchingJsonPath("$.failedReason")));
                  baseRule.verify(1, putRequestedFor(urlMatching(providerPath + "/.*")));
                });
            vertx.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  @Test
  public void testLogMessageHarvestingNotActive(TestContext context) {
    tenantUDPMap.get(TENANTA).remove(1);
    tenantUDPMap
        .get(TENANTA)
        .get(0)
        .getHarvestingConfig()
        .setHarvestingStatus(HarvestingStatus.INACTIVE);

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = loggerContext.getLogger(WorkerVerticle.class);

    ListAppender<ILoggingEvent> newAppender = new ListAppender<>();
    logger.addAppender(newAppender);
    newAppender.start();

    Async async = context.async();
    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    vertx.setPeriodic(
        1000,
        id -> {
          if (vertx.deploymentIDs().size() <= 1) {
            context.verify(
                v -> {
                  assertThat(newAppender.list.stream())
                      .anyMatch(
                          event -> event.getMessage().contains("HarvestingStatus not ACTIVE"));
                  serviceProviderARule.verify(0, getRequestedFor(urlPathEqualTo("/")));
                  baseRule.verify(0, postRequestedFor(urlEqualTo(reportsPath)));
                  baseRule.verify(0, putRequestedFor(urlMatching(providerPath + "/.*")));
                });
            vertx.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  @Test
  public void testLogMessageProviderNotFound(TestContext context) {
    tenantUDPMap.clear();

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = loggerContext.getLogger(WorkerVerticle.class);

    ListAppender<ILoggingEvent> newAppender = new ListAppender<>();
    logger.addAppender(newAppender);
    newAppender.start();

    Async async = context.async();
    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    vertx.setPeriodic(
        1000,
        id -> {
          if (vertx.deploymentIDs().size() <= 1) {
            context.verify(
                v -> {
                  assertThat(newAppender.list.stream())
                      .anyMatch(
                          event ->
                              event.getMessage().contains("dcb0eec3-f63c-440b-adcd-acca2ec44f39")
                                  && event.getMessage().contains("404"));
                  serviceProviderARule.verify(0, getRequestedFor(urlPathEqualTo("/")));
                  baseRule.verify(0, postRequestedFor(urlEqualTo(reportsPath)));
                  baseRule.verify(0, putRequestedFor(urlMatching(providerPath + "/.*")));
                });
            vertx.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  @Test
  public void testServiceProviderFailsInitialization(TestContext context) {
    Async async = context.async();

    UsageDataProvider udp = tenantUDPMap.get(TENANTA).get(0);
    HarvestingConfig harvestingConfig = udp.getHarvestingConfig();
    harvestingConfig.getSushiConfig().setServiceType("wvitpfailinit");
    harvestingConfig
        .withRequestedReports(List.of("TR"))
        .withReportRelease(5)
        .withHarvestingStart("2021-01")
        .withHarvestingEnd("2021-03");

    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/" + udp.getId())
        .then()
        .statusCode(200);

    vertx.setPeriodic(
        1000,
        id -> {
          if (vertx.deploymentIDs().size() <= 1) {
            context.verify(
                v -> {
                  serviceProviderARule.verify(0, getRequestedFor(urlPathEqualTo("/")));
                  serviceProviderBRule.verify(0, getRequestedFor(urlPathEqualTo("/")));
                  baseRule.verify(
                      3,
                      postRequestedFor(urlEqualTo(reportsPath))
                          .withRequestBody(
                              matchingJsonPath(
                                  "$.failedReason",
                                  equalTo(
                                      "Failed getting ServiceEndpoint: "
                                          + "java.lang.RuntimeException: Initialization error"))));
                  baseRule.verify(
                      1,
                      postRequestedFor(urlEqualTo(reportsPath))
                          .withRequestBody(matchingJsonPath("$.yearMonth", equalTo("2021-03"))));
                  baseRule.verify(1, putRequestedFor(urlMatching(providerPath + "/.*")));
                });
            vertx.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  @Test
  public void testNumberOfRequestsMadeForProvider(TestContext context) {
    Async async = context.async();

    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    vertx.setPeriodic(
        1000,
        id -> {
          if (vertx.deploymentIDs().size() <= 1) {
            context.verify(
                v -> {
                  serviceProviderARule.verify(3, getRequestedFor(urlPathEqualTo("/")));
                  serviceProviderARule.verify(
                      1,
                      getRequestedFor(urlPathEqualTo("/"))
                          .withQueryParam("report", equalTo("JR1"))
                          .withQueryParam("begin", equalTo("2020-01-01"))
                          .withQueryParam("end", equalTo("2020-04-30")));
                  serviceProviderBRule.verify(0, getRequestedFor(urlPathEqualTo("/")));
                  baseRule.verify(28, postRequestedFor(urlEqualTo(reportsPath)));
                  baseRule.verify(
                      1,
                      postRequestedFor(urlEqualTo(reportsPath))
                          .withRequestBody(matchingJsonPath("$.report.month", equalTo("2020-03"))));
                  baseRule.verify(1, putRequestedFor(urlMatching(providerPath + "/.*")));
                });
            vertx.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  @Test
  public void testFailedReasonIsExceptionToStringIfGetMessageIsNull(TestContext context) {
    tenantUDPMap
        .get(TENANTA)
        .forEach(
            udp -> {
              HarvestingConfig harvestingConfig = udp.getHarvestingConfig();
              harvestingConfig.getSushiConfig().setServiceType("wvitpfail");
              harvestingConfig
                  .withRequestedReports(List.of("TR"))
                  .withReportRelease(5)
                  .withHarvestingStart("2021-01")
                  .withHarvestingEnd("2021-01");
            });

    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    Async async = context.async();
    vertx.setPeriodic(
        1000,
        id -> {
          if (vertx.deploymentIDs().size() <= 1) {
            context.verify(
                v ->
                    baseRule.verify(
                        1,
                        postRequestedFor(urlEqualTo(reportsPath))
                            .withRequestBody(matchingJsonPath("$.report", absent()))
                            .withRequestBody(matchingJsonPath("$.yearMonth", equalTo("2021-01")))
                            .withRequestBody(
                                matchingJsonPath(
                                    "$.failedReason", equalTo(new Exception().toString())))));
            vertx.cancelTimer(id);
            async.complete();
          }
        });
  }

  static class UDPResponseTransformer extends ResponseDefinitionTransformer {

    @Override
    public ResponseDefinition transform(
        Request request,
        ResponseDefinition responseDefinition,
        FileSource files,
        Parameters parameters) {

      String tenant = request.getHeader(XOkapiHeaders.TENANT);
      Matcher matcher = providerPathWithIdPattern.matcher(request.getUrl());
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
