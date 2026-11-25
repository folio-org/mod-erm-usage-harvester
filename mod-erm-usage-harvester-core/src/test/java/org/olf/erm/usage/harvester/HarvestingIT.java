package org.olf.erm.usage.harvester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.TestUtil.ENTITLEMENTS_URL_PATTERN;
import static org.olf.erm.usage.harvester.TestUtil.shutdownSchedulers;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.apache.logging.log4j.message.Message;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.CounterReports;
import org.folio.rest.jaxrs.model.ErrorHandling;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestingStatus;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.jaxrs.model.UsageDataProviders;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.client.ExtCounterReportsClientImpl;
import org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClientImpl;
import org.olf.erm.usage.harvester.endpoints.WorkerVerticleITProviderLifecycle;
import org.olf.erm.usage.harvester.errorhandling.ErrorHandlingPgUtil;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

@RunWith(VertxUnitRunner.class)
public class HarvestingIT {

  private static final Vertx VERTX = Vertx.vertx();
  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final Map<String, String> OKAPI_HEADERS = Map.of(XOkapiHeaders.TENANT, TENANT_A);
  private static final List<String> TENANTS = List.of(TENANT_A, TENANT_B);
  private static final String ERROR_HANDLING_TABLE = "error_handling";
  private static final String HARVESTER_PATH = "/erm-usage-harvester";
  private static final String HARVESTER_START_PATH = "/erm-usage-harvester/start";
  private static final Map<String, List<UsageDataProvider>> TENANT_UDP_MAP = new HashMap<>();

  private static final String REPORTS_PATH = ExtCounterReportsClientImpl.PATH;
  private static final String PROVIDER_PATH = ExtUsageDataProvidersClientImpl.PATH;
  private static final Pattern PROVIDER_PATH_WITH_ID_PATTERN =
      Pattern.compile(PROVIDER_PATH + "/(.{8}-.{4}-.{4}-.{4}-.{12}).*");

  private static final ListAppender LIST_APPENDER = new ListAppender("listAppender");

  private static String okapiUrl;

  @ClassRule
  public static PostgresContainerRule pgContainerRule =
      new PostgresContainerRule(VERTX, TENANTS.toArray(String[]::new));

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
    TENANT_UDP_MAP.clear();
    UsageDataProvider udp1 =
        new UsageDataProvider()
            .withId("dcb0eec3-f63c-440b-adcd-acca2ec44f39")
            .withLabel("Provider A")
            .withHarvestingConfig(
                new HarvestingConfig()
                    .withRequestedReports(List.of("JR1"))
                    .withReportRelease("4")
                    .withHarvestingStart("2018-01")
                    .withHarvestingEnd("2020-04")
                    .withHarvestingStatus(HarvestingStatus.ACTIVE)
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
                    .withReportRelease("4")
                    .withHarvestingStart("2018-01")
                    .withHarvestingEnd("2020-04")
                    .withHarvestingStatus(HarvestingStatus.ACTIVE)
                    .withSushiConfig(
                        new SushiConfig()
                            .withServiceType("wvitp")
                            .withServiceUrl(serviceProviderBRule.baseUrl())))
            .withSushiCredentials(
                new SushiCredentials()
                    .withApiKey("apiKey")
                    .withCustomerId("custId")
                    .withRequestorId("reqId"));
    TENANT_UDP_MAP.put(TENANT_A, new ArrayList<>(List.of(udp1, udp2)));
  }

  @BeforeClass
  public static void beforeClass(TestContext context) throws SchedulerException {
    // start from a fresh default scheduler, whatever earlier ITs left behind
    shutdownSchedulers();
    okapiUrl = baseRule.baseUrl();
    int httpPort = NetworkUtils.nextFreePort();

    JsonObject cfg = new JsonObject().put("okapiUrl", okapiUrl).put("http.port", httpPort);

    baseRule.stubFor(
        get(urlMatching(HARVESTER_PATH + "/.*"))
            .willReturn(aResponse().proxiedFrom("http://localhost:" + httpPort)));

    JsonArray entitlementsResponse =
        TENANTS.stream().collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
    baseRule.stubFor(
        get(urlPathMatching(ENTITLEMENTS_URL_PATTERN))
            .willReturn(
                aResponse().withStatus(200).withBody(entitlementsResponse.encodePrettily())));

    baseRule.stubFor(
        get(urlMatching(PROVIDER_PATH + "/.*"))
            .willReturn(aResponse().withTransformers("UDPResponseTransformer")));

    baseRule.stubFor(
        put(urlMatching(PROVIDER_PATH + "/.*")).willReturn(aResponse().withStatus(204)));

    // add listAppender to root logger
    org.apache.logging.log4j.core.Logger root =
        (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
    LIST_APPENDER.start();
    root.addAppender(LIST_APPENDER);

    VERTX
        .deployVerticle(RestVerticle.class.getName(), new DeploymentOptions().setConfig(cfg))
        .onComplete(context.asyncAssertSuccess());
  }

  @AfterClass
  public static void afterClass() throws SchedulerException {
    shutdownSchedulers();
  }

  @Before
  public void before() {
    resetTenantUDPMap();
    deleteErrorHandling();
    baseRule.resetRequests();
    serviceProviderARule.resetRequests();
    serviceProviderBRule.resetRequests();

    baseRule.stubFor(
        get(urlPathEqualTo(PROVIDER_PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        Json.encodePrettily(
                            new UsageDataProviders()
                                .withUsageDataProviders(TENANT_UDP_MAP.get(TENANT_A))
                                .withTotalRecords(TENANT_UDP_MAP.get(TENANT_A).size())))));

    baseRule.stubFor(
        get(urlPathEqualTo(REPORTS_PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        Json.encodePrettily(
                            new CounterReports()
                                .withCounterReports(Collections.emptyList())
                                .withTotalRecords(0)))));

    baseRule.stubFor(post(urlPathEqualTo(REPORTS_PATH)).willReturn(aResponse().withStatus(201)));

    serviceProviderARule.stubFor(
        get(anyUrl())
            .willReturn(aResponse().withStatus(200).withBody(Json.encodePrettily(new Report()))));

    serviceProviderBRule.stubFor(
        get(anyUrl())
            .willReturn(aResponse().withStatus(200).withBody(Json.encodePrettily(new Report()))));

    LIST_APPENDER.clear(); // reset ListAppender
  }

  @Test
  public void testNumberOfRequestsMadeForTenant(TestContext context) {
    Async async = context.async();

    given().headers(OKAPI_HEADERS).get(okapiUrl + HARVESTER_START_PATH).then().statusCode(200);

    VERTX.setPeriodic(
        1000,
        id -> {
          if (VERTX.deploymentIDs().size() <= 1) {
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
                      postRequestedFor(urlEqualTo(REPORTS_PATH))
                          .withRequestBody(matchingJsonPath("$.report.month", equalTo("2020-03"))));
                  baseRule.verify(28 * 2, postRequestedFor(urlEqualTo(REPORTS_PATH)));
                  baseRule.verify(2, putRequestedFor(urlMatching(PROVIDER_PATH + "/.*")));
                });
            VERTX.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  @Test
  public void testNumberOfRequestsMadeForProviderWithErrors(TestContext context) {
    TENANT_UDP_MAP
        .get(TENANT_A)
        .forEach(udp -> udp.getHarvestingConfig().getSushiConfig().setServiceType("wvitp2"));

    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    Async async = context.async();
    VERTX.setPeriodic(
        1000,
        id -> {
          if (VERTX.deploymentIDs().size() <= 1) {
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
                  baseRule.verify(28, postRequestedFor(urlEqualTo(REPORTS_PATH)));
                  baseRule.verify(
                      1,
                      postRequestedFor(urlEqualTo(REPORTS_PATH))
                          .withRequestBody(matchingJsonPath("$.report.month", equalTo("2020-03"))));
                  baseRule.verify(
                      1,
                      postRequestedFor(urlEqualTo(REPORTS_PATH))
                          .withRequestBody(matchingJsonPath("$.report", absent()))
                          .withRequestBody(
                              matchingJsonPath(
                                  "$.failedReason", matching("Report not valid:.*2018-03"))));
                  baseRule.verify(1, putRequestedFor(urlMatching(PROVIDER_PATH + "/.*")));
                });
            VERTX.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  @Test
  public void testNumberOfRequestsMadeForProviderWithTooManyRequestsError(TestContext context) {
    TENANT_UDP_MAP
        .get(TENANT_A)
        .forEach(udp -> udp.getHarvestingConfig().getSushiConfig().setServiceType("wvitp3"));

    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    Async async = context.async();
    VERTX.setPeriodic(
        1000,
        id -> {
          if (VERTX.deploymentIDs().size() <= 1) {
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
                  baseRule.verify(28, postRequestedFor(urlEqualTo(REPORTS_PATH)));
                  baseRule.verify(1, putRequestedFor(urlMatching(PROVIDER_PATH + "/.*")));
                });
            VERTX.cancelTimer(id);
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
    VERTX.setPeriodic(
        1000,
        id -> {
          if (VERTX.deploymentIDs().size() <= 1) {
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
                  baseRule.verify(28, postRequestedFor(urlEqualTo(REPORTS_PATH)));
                  baseRule.verify(
                      28,
                      postRequestedFor(urlEqualTo(REPORTS_PATH))
                          .withRequestBody(matchingJsonPath("$.report", absent()))
                          .withRequestBody(matchingJsonPath("$.failedReason")));
                  baseRule.verify(1, putRequestedFor(urlMatching(PROVIDER_PATH + "/.*")));
                });
            VERTX.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  @Test
  public void testFetchListEmpty(TestContext context) {
    UsageDataProvider usageDataProvider = TENANT_UDP_MAP.get(TENANT_A).getFirst();
    usageDataProvider.getHarvestingConfig().setHarvestingStart("2018-01");
    usageDataProvider.getHarvestingConfig().setHarvestingEnd("2018-03");

    UUID uuid = UUID.randomUUID();
    List<CounterReport> reports =
        Stream.iterate(YearMonth.of(2018, 1), m -> m.plusMonths(1))
            .limit(3)
            .map(
                m ->
                    new CounterReport()
                        .withReport(new Report())
                        .withProviderId(uuid.toString())
                        .withYearMonth(m.toString()))
            .toList();

    baseRule.stubFor(
        get(urlPathEqualTo(REPORTS_PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        Json.encodePrettily(
                            new CounterReports()
                                .withCounterReports(reports)
                                .withTotalRecords(reports.size())))));

    Async async = context.async();
    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    VERTX.setPeriodic(
        1000,
        id -> {
          if (VERTX.deploymentIDs().size() <= 1) {
            context.verify(
                v -> {
                  assertThat(
                          LIST_APPENDER.getEvents().stream()
                              .map(LogEvent::getMessage)
                              .map(Message::getFormattedMessage))
                      .anyMatch(msg -> msg.contains("No reports need to be fetched"));
                  serviceProviderARule.verify(0, getRequestedFor(urlPathEqualTo("/")));
                  baseRule.verify(1, getRequestedFor(urlPathEqualTo(REPORTS_PATH)));
                  baseRule.verify(0, postRequestedFor(urlEqualTo(REPORTS_PATH)));
                  baseRule.verify(1, putRequestedFor(urlMatching(PROVIDER_PATH + "/.*")));
                });
            VERTX.cancelTimer(id);
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

    VERTX.setPeriodic(
        1000,
        id -> {
          if (VERTX.deploymentIDs().size() <= 1) {
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
                  baseRule.verify(28, postRequestedFor(urlEqualTo(REPORTS_PATH)));
                  baseRule.verify(
                      1,
                      postRequestedFor(urlEqualTo(REPORTS_PATH))
                          .withRequestBody(matchingJsonPath("$.report.month", equalTo("2020-03"))));
                  baseRule.verify(1, putRequestedFor(urlMatching(PROVIDER_PATH + "/.*")));
                });
            VERTX.cancelTimer(id);
            async.complete();
          }
        });

    async.await(10000);
  }

  @Test
  public void testMaxFailedAttemptsDefaultIfNothingIsStored(TestContext context) {
    harvestExistingMonth(context);

    verifyReportsQueriedWithMaxFailedAttempts(5);
  }

  @Test
  public void testMaxFailedAttemptsGlobalSetting(TestContext context) {
    saveErrorHandling(new ErrorHandling().withMaxFailedAttempts(7));

    harvestExistingMonth(context);

    verifyReportsQueriedWithMaxFailedAttempts(7);
  }

  @Test
  public void testRepeatedRunsDoNotCreateVertxInstances(TestContext context) {
    long vertxInstancesBefore = countVertxInstances();

    for (int i = 1; i <= 3; i++) {
      harvestWithLifecycleProvider(context);
    }

    assertThat(countVertxInstances()).isEqualTo(vertxInstancesBefore);
  }

  /** Each Vertx instance runs exactly one blocked-thread-checker thread. */
  private static long countVertxInstances() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(Thread::isAlive)
        .filter(t -> t.getName().startsWith("vertx-blocked-thread-checker"))
        .count();
  }

  /** Harvests provider A with {@link WorkerVerticleITProviderLifecycle} and waits for the end. */
  private void harvestWithLifecycleProvider(TestContext context) {
    WorkerVerticleITProviderLifecycle.reset();
    var usageDataProvider = TENANT_UDP_MAP.get(TENANT_A).getFirst();
    var providerId = usageDataProvider.getId();
    usageDataProvider
        .getHarvestingConfig()
        .getSushiConfig()
        .setServiceType(WorkerVerticleITProviderLifecycle.SERVICE_TYPE);

    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    waitUntil(
        context,
        () ->
            WorkerVerticleITProviderLifecycle.CLOSED.get() > 0
                && VERTX.deploymentIDs().size() <= 1
                && !isJobScheduled(new JobKey(providerId, TENANT_A)));
  }

  private static boolean isJobScheduled(JobKey jobKey) {
    try {
      return StdSchedulerFactory.getDefaultScheduler().checkExists(jobKey);
    } catch (SchedulerException e) {
      return false;
    }
  }

  private static void verifyReportsQueriedWithMaxFailedAttempts(int expected) {
    baseRule.verify(
        1,
        getRequestedFor(urlPathEqualTo(REPORTS_PATH))
            .withQueryParam("query", containing("failedAttempts>=" + expected + ")")));
  }

  /** Harvests one month that already exists, so the worker has nothing to fetch and ends soon. */
  private void harvestExistingMonth(TestContext context) {
    String month = "2018-01";
    HarvestingConfig harvestingConfig =
        TENANT_UDP_MAP.get(TENANT_A).getFirst().getHarvestingConfig();
    harvestingConfig.setHarvestingStart(month);
    harvestingConfig.setHarvestingEnd(month);
    baseRule.stubFor(
        get(urlPathEqualTo(REPORTS_PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        Json.encodePrettily(
                            new CounterReports()
                                .withCounterReports(
                                    List.of(new CounterReport().withYearMonth(month)))
                                .withTotalRecords(1)))));

    given()
        .headers(OKAPI_HEADERS)
        .get(okapiUrl + HARVESTER_START_PATH + "/dcb0eec3-f63c-440b-adcd-acca2ec44f39")
        .then()
        .statusCode(200);

    waitUntil(
        context,
        () ->
            !baseRule.findAll(getRequestedFor(urlPathEqualTo(REPORTS_PATH))).isEmpty()
                && VERTX.deploymentIDs().size() <= 1);
  }

  private static void waitUntil(TestContext context, BooleanSupplier condition) {
    Async async = context.async();
    long timerId =
        VERTX.setPeriodic(
            100,
            id -> {
              if (condition.getAsBoolean()) {
                VERTX.cancelTimer(id);
                async.complete();
              }
            });
    try {
      async.await(10000);
    } finally {
      VERTX.cancelTimer(timerId);
    }
  }

  private static void saveErrorHandling(ErrorHandling errorHandling) {
    ErrorHandlingPgUtil.upsert(VERTX.getOrCreateContext(), TENANT_A, errorHandling)
        .toCompletionStage()
        .toCompletableFuture()
        .join();
  }

  private static void deleteErrorHandling() {
    PostgresClient.getInstance(VERTX, TENANT_A)
        .delete(ERROR_HANDLING_TABLE, new Criterion(new Criteria()))
        .toCompletionStage()
        .toCompletableFuture()
        .join();
  }

  static class UDPResponseTransformer extends ResponseDefinitionTransformer {

    @Override
    public ResponseDefinition transform(
        Request request,
        ResponseDefinition responseDefinition,
        FileSource files,
        Parameters parameters) {

      String tenant = request.getHeader(XOkapiHeaders.TENANT);
      Matcher matcher = PROVIDER_PATH_WITH_ID_PATTERN.matcher(request.getUrl());
      String uuid = (matcher.matches()) ? matcher.group(1) : "";

      return Optional.ofNullable(TENANT_UDP_MAP.get(tenant))
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
