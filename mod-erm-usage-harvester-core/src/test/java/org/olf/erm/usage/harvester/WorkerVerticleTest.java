package org.olf.erm.usage.harvester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
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
import static org.olf.erm.usage.harvester.TestUtil.createSampleUsageDataProvider;

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
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.CounterReports;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestVia;
import org.folio.rest.jaxrs.model.Report;
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
      new WorkerVerticle(tenantId, "someTokem", "providerId");
  private static final Vertx vertx = Vertx.vertx();

  private static String reportsPath;
  private static String providerPath;
  private static String aggregatorPath;
  private static String modConfigurationPath;

  @BeforeClass
  public static void beforeClass(TestContext context) throws IOException {
    String deployCfg =
        Resources.toString(Resources.getResource("config.json"), StandardCharsets.UTF_8);
    JsonObject cfg = new JsonObject(deployCfg);
    cfg.put("okapiUrl", wireMockRule.baseUrl());
    cfg.put("testing", true);

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
}
