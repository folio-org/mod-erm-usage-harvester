package org.olf.erm.usage.harvester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.CounterReports;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestVia;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestingStatus;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;

@RunWith(VertxUnitRunner.class)
public class WorkerVerticleTest {

  @ClassRule
  public static WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort(), false);

  @Rule public Timeout timeoutRule = Timeout.seconds(10);

  private static final String tenantId = "diku";
  private static final WorkerVerticle harvester =
      new WorkerVerticle(Map.of(XOkapiHeaders.TENANT, tenantId, XOkapiHeaders.TOKEN, "someToken"));
  private static final String UDP_ID = "eff7063d-9ab9-49e9-8bca-5e40863455d4";
  private static final Vertx vertx = Vertx.vertx();

  private static String reportsPath;
  private static String providerPath;
  private static String aggregatorPath;
  private static String modConfigurationPath;
  private static CounterReport cr;

  @BeforeClass
  public static void beforeClass(TestContext context) throws IOException {
    String deployCfg =
        Resources.toString(Resources.getResource("config.json"), StandardCharsets.UTF_8);
    JsonObject cfg = new JsonObject(deployCfg);
    cfg.put("okapiUrl", wireMockRule.baseUrl());
    cfg.put("testing", true);
    final String str =
        Resources.toString(Resources.getResource("counterreport-sample.json"), Charsets.UTF_8);
    cr = Json.decodeValue(str, CounterReport.class);

    reportsPath = cfg.getString("reportsPath");
    providerPath = cfg.getString("providerPath");
    aggregatorPath = cfg.getString("aggregatorPath");
    modConfigurationPath = cfg.getString("modConfigurationPath");

    vertx.deployVerticle(
        harvester, new DeploymentOptions().setConfig(cfg), context.asyncAssertSuccess());
  }

  @Before
  public void setUp() {
    stubFor(
        get(urlPathEqualTo(modConfigurationPath))
            .willReturn(aResponse().withStatus(404).withFault(Fault.EMPTY_RESPONSE)));
  }

  @After
  public void tearDown() {
    wireMockRule.resetAll();
  }

  @Test
  public void getProvidersBodyValid(TestContext context) {
    stubFor(
        get(urlPathMatching(providerPath))
            .willReturn(aResponse().withBodyFile("usage-data-providers.json")));

    harvester
        .getActiveProviders()
        .onComplete(
            context.asyncAssertSuccess(udps -> assertThat(udps.getTotalRecords()).isEqualTo(3)));
  }

  @Test
  public void getProvidersBodyInvalid(TestContext context) {
    stubFor(get(urlPathMatching(providerPath)).willReturn(aResponse().withBody("")));

    harvester
        .getActiveProviders()
        .onComplete(
            context.asyncAssertFailure(t -> assertThat(t).hasMessageContaining("Error decoding")));
  }

  @Test
  public void getProvidersResponseInvalid(TestContext context) {
    stubFor(get(urlPathMatching(providerPath)).willReturn(aResponse().withStatus(404)));

    harvester
        .getActiveProviders()
        .onComplete(context.asyncAssertFailure(t -> assertThat(t).hasMessageContaining("404")));
  }

  @Test
  public void getProvidersNoService(TestContext context) {
    wireMockRule.resetAll();

    harvester.getActiveProviders().onComplete(context.asyncAssertFailure());
  }

  @Test
  public void getAggregatorSettingsBodyValid(TestContext context) throws IOException {
    final UsageDataProvider provider =
        new ObjectMapper()
            .readValue(
                Resources.toString(
                    Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
                UsageDataProvider.class);
    stubFor(
        get(urlEqualTo(
                aggregatorPath + "/" + provider.getHarvestingConfig().getAggregator().getId()))
            .willReturn(aResponse().withBodyFile("aggregator-setting.json")));

    harvester
        .getAggregatorSetting(provider)
        .onComplete(
            context.asyncAssertSuccess(
                as -> assertThat(as.getLabel()).isEqualTo("Nationaler Statistikserver")));
  }

  @Test
  public void getAggregatorSettingsBodyValidNoAggregator(TestContext context) throws IOException {
    final UsageDataProvider provider1 =
        new ObjectMapper()
            .readValue(
                Resources.toString(
                    Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
                UsageDataProvider.class);
    final UsageDataProvider provider2 =
        new ObjectMapper()
            .readValue(
                Resources.toString(
                    Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
                UsageDataProvider.class);

    provider1.getHarvestingConfig().setAggregator(null);
    Async async = context.async();
    harvester
        .getAggregatorSetting(provider1)
        .onComplete(
            ar -> {
              context.verify(
                  v -> {
                    assertThat(ar.failed()).isTrue();
                    assertThat(ar.cause().getMessage()).contains("no aggregator found");
                  });
              async.complete();
            });

    provider2.getHarvestingConfig().getAggregator().setId(null);
    Async async2 = context.async();
    harvester
        .getAggregatorSetting(provider2)
        .onComplete(
            ar -> {
              context.verify(
                  v -> {
                    assertThat(ar.failed()).isTrue();
                    assertThat(ar.cause().getMessage()).contains("no aggregator found");
                  });
              async2.complete();
            });
  }

  @Test
  public void getAggregatorSettingsBodyInvalid(TestContext context) throws IOException {
    final UsageDataProvider provider =
        new ObjectMapper()
            .readValue(
                Resources.toString(
                    Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
                UsageDataProvider.class);
    stubFor(
        get(urlEqualTo(
                aggregatorPath + "/" + provider.getHarvestingConfig().getAggregator().getId()))
            .willReturn(aResponse().withBody("garbage")));

    harvester
        .getAggregatorSetting(provider)
        .onComplete(
            context.asyncAssertFailure(t -> assertThat(t).hasMessageContaining("Error decoding")));
  }

  @Test
  public void getAggregatorSettingsResponseInvalid(TestContext context) throws IOException {
    final UsageDataProvider provider =
        new ObjectMapper()
            .readValue(
                Resources.toString(
                    Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
                UsageDataProvider.class);
    stubFor(
        get(urlEqualTo(
                aggregatorPath + "/" + provider.getHarvestingConfig().getAggregator().getId()))
            .willReturn(
                aResponse().withBody("Aggregator settingObject does not exist").withStatus(404)));

    harvester
        .getAggregatorSetting(provider)
        .onComplete(context.asyncAssertFailure(t -> assertThat(t).hasMessageContaining("404")));
  }

  @Test
  public void getAggregatorSettingsNoService(TestContext context) throws IOException {
    final UsageDataProvider provider =
        new ObjectMapper()
            .readValue(
                Resources.toString(
                    Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
                UsageDataProvider.class);
    wireMockRule.resetAll();

    harvester.getAggregatorSetting(provider).onComplete(context.asyncAssertFailure());
  }

  @Test
  public void createReportJsonObject() throws IOException {
    final UsageDataProvider provider =
        new ObjectMapper()
            .readValue(
                Resources.toString(
                    Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
                UsageDataProvider.class);

    final String reportName = "JR1";
    final String reportData = new JsonObject().put("data", "testreport").toString();
    final YearMonth yearMonth = YearMonth.of(2018, 1);

    CounterReport result =
        ServiceEndpoint.createCounterReport(reportData, reportName, provider, yearMonth);
    assertThat(result)
        .isNotNull()
        .satisfies(
            cr -> {
              assertThat(cr.getReportName()).isEqualTo(reportName);
              assertThat(Json.encode(cr.getReport())).isEqualTo(reportData);
              assertThat(cr.getYearMonth()).isEqualTo(yearMonth.toString());
            });
  }

  @Test
  public void postReportNoExisting(TestContext context) {
    final String url = reportsPath;
    stubFor(
        get(urlPathEqualTo(url))
            .willReturn(aResponse().withStatus(200).withBodyFile("counter-reports-empty.json")));
    stubFor(post(urlEqualTo(url)).willReturn(aResponse().withStatus(201)));

    harvester
        .postReport(cr)
        .onComplete(context.asyncAssertSuccess(v -> verify(postRequestedFor(urlEqualTo(url)))));
  }

  @Test
  public void postReportExisting(TestContext context) {
    final String url = reportsPath;
    final String urlId = url + "/43d7e87c-fb32-4ce2-81f9-11fe75c29bbb";
    stubFor(
        get(urlPathEqualTo(url))
            .willReturn(aResponse().withStatus(200).withBodyFile("counter-reports-one.json")));
    stubFor(put(urlEqualTo(urlId)).willReturn(aResponse().withStatus(201)));

    harvester
        .postReport(cr)
        .onComplete(context.asyncAssertSuccess(v -> verify(putRequestedFor(urlEqualTo(urlId)))));
  }

  @Test
  public void testGetServiceEndpoint(TestContext context) throws IOException {
    final UsageDataProvider provider =
        new ObjectMapper()
            .readValue(
                Resources.toString(
                    Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
                UsageDataProvider.class);

    harvester
        .getServiceEndpoint(provider)
        .onComplete(context.asyncAssertSuccess(sep -> assertThat(sep).isNotNull()));
  }

  @Test
  public void testGetServiceEndpointNoImplementation(TestContext context) throws IOException {
    final UsageDataProvider provider =
        new ObjectMapper()
            .readValue(
                Resources.toString(
                    Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
                UsageDataProvider.class);
    provider.getHarvestingConfig().getSushiConfig().setServiceType("test3");

    harvester
        .getServiceEndpoint(provider)
        .onComplete(
            context.asyncAssertFailure(
                t -> assertThat(t).hasMessageContaining("No service implementation")));
  }

  @Test
  public void testGetServiceEndpointAggregator(TestContext context)
      throws DecodeException, IOException {
    final UsageDataProvider provider =
        Json.decodeValue(
            Resources.toString(
                Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
            UsageDataProvider.class);
    provider.getHarvestingConfig().setHarvestVia(HarvestVia.AGGREGATOR);

    stubFor(
        get(urlEqualTo(
                aggregatorPath + "/" + provider.getHarvestingConfig().getAggregator().getId()))
            .willReturn(aResponse().withBodyFile("aggregator-setting.json")));

    harvester
        .getServiceEndpoint(provider)
        .onComplete(context.asyncAssertSuccess(sep -> assertThat(sep).isNotNull()));
  }

  @Test
  public void testGetServiceEndpointAggregatorNull(TestContext context)
      throws DecodeException, IOException {
    final UsageDataProvider provider =
        Json.decodeValue(
            Resources.toString(
                Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
            UsageDataProvider.class);
    provider.getHarvestingConfig().setHarvestVia(HarvestVia.AGGREGATOR);
    provider.getHarvestingConfig().setAggregator(null);

    harvester
        .getServiceEndpoint(provider)
        .onComplete(context.asyncAssertSuccess(sep -> assertThat(sep).isNotNull()));
  }

  @Test
  public void testGetServiceEndpointAggregatorIdNull(TestContext context)
      throws DecodeException, IOException {
    final UsageDataProvider provider =
        Json.decodeValue(
            Resources.toString(
                Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
            UsageDataProvider.class);
    provider.getHarvestingConfig().setHarvestVia(HarvestVia.AGGREGATOR);
    provider.getHarvestingConfig().getAggregator().setId(null);

    harvester
        .getServiceEndpoint(provider)
        .onComplete(context.asyncAssertSuccess(sep -> assertThat(sep).isNotNull()));
  }

  private CounterReports createCounterSampleReports() {
    UUID uuid = UUID.randomUUID();
    List<CounterReport> reports =
        Stream.iterate(YearMonth.of(2017, 12), m -> m.plusMonths(1))
            .limit(3)
            .map(
                m ->
                    new CounterReport()
                        .withReport(new Report())
                        .withProviderId(uuid.toString())
                        .withYearMonth(m.toString()))
            .collect(Collectors.toList());
    return new CounterReports().withCounterReports(reports);
  }

  @Test
  public void testGetValidMonths(TestContext context) {
    String encode = Json.encodePrettily(createCounterSampleReports());
    stubFor(
        get(urlPathEqualTo(reportsPath)).willReturn(aResponse().withStatus(200).withBody(encode)));

    harvester
        .getValidMonths("providerId", "JR1", YearMonth.of(2017, 12), YearMonth.of(2018, 2))
        .onComplete(
            context.asyncAssertSuccess(
                list ->
                    assertThat(list)
                        .isEqualTo(
                            Arrays.asList(
                                YearMonth.of(2017, 12),
                                YearMonth.of(2018, 1),
                                YearMonth.of(2018, 2)))));
  }

  @Test
  public void testGetValidMonthsFail(TestContext context) {
    stubFor(get(urlPathEqualTo(reportsPath)).willReturn(aResponse().withStatus(500)));
    harvester
        .getValidMonths("providerId", "JR1", YearMonth.of(2017, 12), YearMonth.of(2018, 2))
        .onComplete(
            context.asyncAssertFailure(
                t ->
                    assertThat(t)
                        .hasMessageContaining("Received status code")
                        .hasMessageContaining("500")));
  }

  @Test
  public void testGetFetchListHarvestingNotActive(TestContext context) {
    UsageDataProvider provider =
        new UsageDataProvider()
            .withHarvestingConfig(
                new HarvestingConfig().withHarvestingStatus(HarvestingStatus.INACTIVE));

    harvester
        .getFetchList(provider)
        .onComplete(
            context.asyncAssertFailure(t -> assertThat(t).hasMessageContaining("not active")));
  }

  @Test
  public void testGetFetchList(TestContext context) {
    UsageDataProvider provider = createSampleUsageDataProvider();
    provider.getHarvestingConfig().setHarvestingEnd("2018-03");

    stubFor(
        get(urlPathEqualTo(reportsPath))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(Json.encodePrettily(createCounterSampleReports()))));

    harvester
        .getFetchList(provider)
        .onComplete(
            context.asyncAssertSuccess(
                list -> {
                  final String begin = "2018-03-01";
                  final String end = "2018-03-31";
                  assertThat(list)
                      .hasSize(3)
                      .containsExactlyInAnyOrder(
                          new FetchItem("JR1", begin, end),
                          new FetchItem("JR2", begin, end),
                          new FetchItem("JR3", begin, end));
                  verify(exactly(3), getRequestedFor(urlPathEqualTo("/counter-reports")));
                }));
  }

  @Test
  public void testNumberOfRequestsMadeByFetchAndPostReportsRx(TestContext context) {
    StubMapping existingReportsStub =
        stubFor(
            get(urlPathEqualTo(reportsPath))
                .withQueryParam("query", matching(".*failedAttempts.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(Json.encodePrettily(createCounterSampleReports()))));

    StubMapping additionalReportStub =
        stubFor(
            get(urlPathEqualTo(reportsPath))
                .withQueryParam("query", matching("^(?!.*failedAttempts).*2018-03.*JR1.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(
                            Json.encodePrettily(
                                new CounterReports()
                                    .withCounterReports(
                                        Collections.singletonList(new CounterReport()))))));
    StubMapping nonExistingReportsStub =
        stubFor(
            get(urlPathEqualTo(reportsPath))
                .withQueryParam("query", notMatching(".*failedAttempts.*|.*2018-03.*JR1.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(Json.encodePrettily(new CounterReports()))));

    stubFor(post(urlPathEqualTo(reportsPath)).willReturn(aResponse().withStatus(201)));
    stubFor(put(urlPathMatching(reportsPath + "/.*")).willReturn(aResponse().withStatus(204)));
    stubFor(put(urlPathMatching(providerPath + "/.*")).willReturn(aResponse().withStatus(204)));

    UsageDataProvider provider = createSampleUsageDataProvider();
    Async async = context.async();
    harvester
        .fetchAndPostReportsRx(provider)
        .subscribe(
            () -> {
              context.verify(
                  v -> {
                    verify(3, RequestPatternBuilder.like(existingReportsStub.getRequest()));
                    verify(1, RequestPatternBuilder.like(additionalReportStub.getRequest()));
                    verify(5, RequestPatternBuilder.like(nonExistingReportsStub.getRequest()));
                    verify(5, postRequestedFor(urlPathEqualTo(reportsPath)));
                    verify(1, putRequestedFor(urlPathMatching(reportsPath + "/.*")));
                    verify(1, putRequestedFor(urlPathMatching(providerPath + "/.*")));
                    List<LoggedRequest> all = wireMockRule.findAll(anyRequestedFor(anyUrl()));
                    assertThat(all)
                        .hasSize(
                            16); // 16 (configuration call is only made during verticle deployment)
                  });
              async.complete();
            },
            context::fail);
  }

  private UsageDataProvider createSampleUsageDataProvider() {
    String uuid = "97329ea7-f351-458a-a460-71aa6db75e35";
    return new UsageDataProvider()
        .withId(uuid)
        .withLabel("TestProvider")
        .withSushiCredentials(new SushiCredentials().withCustomerId("Customer123"))
        .withHarvestingConfig(
            new HarvestingConfig()
                .withHarvestingStatus(HarvestingStatus.ACTIVE)
                .withHarvestVia(HarvestVia.SUSHI)
                .withSushiConfig(new SushiConfig().withServiceType("test1"))
                .withReportRelease(4)
                .withHarvestingStart("2017-12")
                .withHarvestingEnd("2018-04")
                .withRequestedReports(Arrays.asList("JR1", "JR2", "JR3")));
  }

  @Test
  public void testGetModConfigurationValue(TestContext context) {
    JsonObject response =
        new JsonObject()
            .put(
                "configs",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("module", "testmodule")
                            .put("confiName", "testing")
                            .put("value", "5")));
    stubFor(
        get(urlPathEqualTo(modConfigurationPath))
            .withQueryParam("query", equalTo("(module = testmodule and configName = ok)"))
            .willReturn(aResponse().withStatus(200).withBody(response.encodePrettily())));

    stubFor(
        get(urlPathEqualTo(modConfigurationPath))
            .withQueryParam("query", equalTo("(module = testmodule and configName = empty)"))
            .willReturn(aResponse().withStatus(200).withFault(Fault.EMPTY_RESPONSE)));

    Async async = context.async(2);
    harvester
        .getModConfigurationValue("testmodule", "ok", "3")
        .onFailure(context::fail)
        .onSuccess(
            s -> {
              context.verify(v -> assertThat(s).isEqualTo("5"));
              async.countDown();
            });

    harvester
        .getModConfigurationValue("testmodule", "empty", "3")
        .onFailure(context::fail)
        .onSuccess(
            s -> {
              context.verify(v -> assertThat(s).isEqualTo("3"));
              async.countDown();
            });

    async.await();
    wireMockRule.resetAll();
    Async async2 = context.async();
    harvester
        .getModConfigurationValue("testmodule", "something", "2")
        .onFailure(context::fail)
        .onSuccess(
            s -> {
              context.verify(v -> assertThat(s).isEqualTo("2"));
              async2.countDown();
            });

    async.await();
  }

  @Test
  public void testUpdateUDPLastHarvestingDateSuccess(TestContext context) {
    String urlPath = providerPath + "/" + UDP_ID;

    stubFor(put(urlPath).willReturn(aResponse().withStatus(204)));
    harvester
        .updateUDPLastHarvestingDate(new UsageDataProvider().withId(UDP_ID))
        .onComplete(
            context.asyncAssertSuccess(
                v ->
                    verify(
                        1,
                        putRequestedFor(urlEqualTo(urlPath))
                            .withRequestBody(
                                matchingJsonPath("$.harvestingDate", matching("[0-9]*"))))));
  }

  @Test
  public void testUpdateUDPLastHarvestingDateFail(TestContext context) {
    String urlPath = providerPath + "/" + UDP_ID;

    stubFor(put(urlPath).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    harvester
        .updateUDPLastHarvestingDate(new UsageDataProvider().withId(UDP_ID))
        .onComplete(
            context.asyncAssertFailure(
                t -> assertThat(t).hasMessageContaining("Failed updating harvestingDate")));
  }

  @Test
  public void testUpdateUDPLastHarvestingDateWrongStatusCode(TestContext context) {
    String urlPath = providerPath + "/" + UDP_ID;

    stubFor(put(urlPath).willReturn(aResponse().withStatus(400)));
    harvester
        .updateUDPLastHarvestingDate(new UsageDataProvider().withId(UDP_ID))
        .onComplete(
            context.asyncAssertFailure(
                t ->
                    assertThat(t)
                        .hasMessageContaining("Failed updating harvestingDate")
                        .hasMessageContaining("400")));
  }
}
