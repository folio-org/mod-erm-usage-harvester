package org.olf.erm.usage.harvester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.CounterReports;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestVia;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestingStatus;
import org.folio.rest.jaxrs.model.Platform;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.jaxrs.model.Vendor;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(VertxUnitRunner.class)
public class WorkerVerticleTest {

  private static final Logger LOG = LoggerFactory.getLogger(WorkerVerticleTest.class);

  @Rule public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule public Timeout timeoutRule = Timeout.seconds(5);

  private static final String tenantId = "diku";
  private static final Token token =
      Token.createDummy(tenantId, "6bf2a318-17a9-4fd9-a889-8baf665ab3c8", tenantId);
  private static final WorkerVerticle harvester = new WorkerVerticle(token);

  private static CounterReport cr;

  private static final String deployCfg =
      "{\n"
          + "  \"okapiUrl\": \"http://localhost\",\n"
          + "  \"tenantsPath\": \"/_/proxy/tenants\",\n"
          + "  \"reportsPath\": \"/counter-reports\",\n"
          + "  \"providerPath\": \"/usage-data-providers\",\n"
          + "  \"aggregatorPath\": \"/aggregator-settings\",\n"
          + "  \"moduleId\": \"mod-erm-usage-0.0.1\"\n"
          + "}";

  private static Vertx vertx;
  private String reportsPath;
  private String providerPath;
  private String aggregatorPath;

  @BeforeClass
  public static void beforeClass(TestContext context) {
    try {
      final String str =
          Resources.toString(Resources.getResource("counterreport-sample.json"), Charsets.UTF_8);
      cr = Json.decodeValue(str, CounterReport.class);
    } catch (Exception e) {
      context.fail(e);
    }
  }

  @Before
  public void setup(TestContext context) {
    vertx = Vertx.vertx();
    JsonObject cfg = new JsonObject(deployCfg);
    cfg.put("okapiUrl", StringUtils.removeEnd(wireMockRule.url(""), "/"));
    cfg.put("testing", true);
    vertx.deployVerticle(
        harvester,
        new DeploymentOptions().setConfig(cfg),
        context.asyncAssertSuccess(
            h -> {
              reportsPath = harvester.config().getString("reportsPath");
              providerPath = harvester.config().getString("providerPath");
              aggregatorPath = harvester.config().getString("aggregatorPath");
            }));
  }

  @After
  public void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void getProvidersBodyValid(TestContext context) {
    stubFor(
        get(urlPathMatching(providerPath))
            .willReturn(aResponse().withBodyFile("usage-data-providers.json")));

    Async async = context.async();
    harvester
        .getActiveProviders()
        .setHandler(
            ar -> {
              context.assertTrue(ar.succeeded());
              context.assertEquals(3, ar.result().getTotalRecords());
              async.complete();
            });
  }

  @Test
  public void getProvidersBodyInvalid(TestContext context) {
    stubFor(get(urlPathMatching(providerPath)).willReturn(aResponse().withBody("")));

    Async async = context.async();
    harvester
        .getActiveProviders()
        .setHandler(
            ar -> {
              context.assertTrue(ar.failed());
              context.assertTrue(ar.cause().getMessage().contains("Error decoding"));
              async.complete();
            });
  }

  @Test
  public void getProvidersResponseInvalid(TestContext context) {
    stubFor(get(urlPathMatching(providerPath)).willReturn(aResponse().withStatus(404)));

    Async async = context.async();
    harvester
        .getActiveProviders()
        .setHandler(
            ar -> {
              context.assertTrue(ar.failed());
              context.assertTrue(ar.cause().getMessage().contains("404"));
              async.complete();
            });
  }

  @Test
  public void getProvidersNoService(TestContext context) {
    wireMockRule.stop();

    Async async = context.async();
    harvester
        .getActiveProviders()
        .setHandler(
            ar -> {
              context.assertTrue(ar.failed());
              LOG.error(ar.cause().getMessage(), ar.cause());
              async.complete();
            });
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

    Async async = context.async();
    harvester
        .getAggregatorSetting(provider)
        .setHandler(
            ar -> {
              context.assertTrue(ar.succeeded());
              context.assertTrue("Nationaler Statistikserver".equals(ar.result().getLabel()));
              async.complete();
            });
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
        .setHandler(
            ar -> {
              context.assertTrue(ar.failed());
              context.assertTrue(ar.result() == null);
              context.assertTrue(ar.cause().getMessage().contains("no aggregator found"));
              async.complete();
            });

    provider2.getHarvestingConfig().getAggregator().setId(null);
    Async async2 = context.async();
    harvester
        .getAggregatorSetting(provider2)
        .setHandler(
            ar -> {
              context.assertTrue(ar.failed());
              context.assertTrue(ar.result() == null);
              context.assertTrue(ar.cause().getMessage().contains("no aggregator found"));
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

    Async async = context.async();
    harvester
        .getAggregatorSetting(provider)
        .setHandler(
            ar -> {
              context.assertTrue(ar.failed());
              context.assertTrue(ar.result() == null);
              context.assertTrue(ar.cause().getMessage().contains("Error decoding"));
              async.complete();
            });
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

    Async async = context.async();
    harvester
        .getAggregatorSetting(provider)
        .setHandler(
            ar -> {
              context.assertTrue(ar.failed());
              context.assertTrue(ar.result() == null);
              context.assertTrue(ar.cause().getMessage().contains("404"));
              async.complete();
            });
  }

  @Test
  public void getAggregatorSettingsNoService(TestContext context) throws IOException {
    final UsageDataProvider provider =
        new ObjectMapper()
            .readValue(
                Resources.toString(
                    Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
                UsageDataProvider.class);
    wireMockRule.stop();

    Async async = context.async();
    harvester
        .getAggregatorSetting(provider)
        .setHandler(
            ar -> {
              context.assertTrue(ar.failed());
              async.complete();
            });
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
        harvester.createCounterReport(reportData, reportName, provider, yearMonth);
    assertNotNull(result);
    assertEquals(reportName, result.getReportName());
    assertEquals(reportData, Json.encode(result.getReport()));
    assertEquals(yearMonth.toString(), result.getYearMonth());
    assertEquals(provider.getPlatform().getId(), result.getPlatformId());
    assertEquals(provider.getSushiCredentials().getCustomerId(), result.getCustomerId());
  }

  @Test
  public void postReportNoExisting(TestContext context) {
    final String url = reportsPath;
    stubFor(
        get(urlPathEqualTo(url))
            .willReturn(aResponse().withStatus(200).withBodyFile("counter-reports-empty.json")));
    stubFor(post(urlEqualTo(url)).willReturn(aResponse().withStatus(201)));

    Async async = context.async();
    harvester
        .postReport(cr)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                wireMockRule.verify(postRequestedFor(urlEqualTo(url)));
                async.complete();
              } else {
                context.fail(ar.cause());
              }
            });
  }

  @Test
  public void postReportExisting(TestContext context) {
    final String url = reportsPath;
    final String urlId = url + "/43d7e87c-fb32-4ce2-81f9-11fe75c29bbb";
    stubFor(
        get(urlPathEqualTo(url))
            .willReturn(aResponse().withStatus(200).withBodyFile("counter-reports-one.json")));
    stubFor(put(urlEqualTo(urlId)).willReturn(aResponse().withStatus(201)));

    Async async = context.async();
    harvester
        .postReport(cr)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                wireMockRule.verify(putRequestedFor(urlEqualTo(urlId)));
                async.complete();
              } else {
                context.fail(ar.cause());
              }
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

    Async async = context.async();
    harvester
        .getServiceEndpoint(provider)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                context.assertTrue(ar.result() != null);
                async.complete();
              } else {
                context.fail(ar.cause());
              }
            });
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

    Async async = context.async();
    harvester
        .getServiceEndpoint(provider)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                context.fail();
              } else {
                assertThat(ar.cause().getMessage()).contains("No service implementation");
                async.complete();
              }
            });
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

    Async async = context.async();
    harvester
        .getServiceEndpoint(provider)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                assertThat(ar.result()).isNotNull();
                async.complete();
              } else {
                context.fail(ar.cause());
              }
            });
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

    Async async = context.async();
    harvester
        .getServiceEndpoint(provider)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                assertThat(ar.result()).isNotNull();
                async.complete();
              } else {
                context.fail(ar.cause());
              }
            });
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

    Async async = context.async();
    harvester
        .getServiceEndpoint(provider)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                assertThat(ar.result()).isNotNull();
                async.complete();
              } else {
                context.fail(ar.cause());
              }
            });
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
        get(urlPathEqualTo("/counter-reports"))
            .willReturn(aResponse().withStatus(200).withBody(encode)));

    Async async = context.async();
    harvester
        .getValidMonths("providerId", "JR1", YearMonth.of(2017, 12), YearMonth.of(2018, 2))
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                assertThat(ar.result())
                    .isEqualTo(
                        Arrays.asList(
                            YearMonth.of(2017, 12), YearMonth.of(2018, 1), YearMonth.of(2018, 2)));
                async.complete();
              } else {
                context.fail(ar.cause());
              }
            });
  }

  @Test
  public void testGetValidMonthsFail(TestContext context) {
    stubFor(get(urlPathEqualTo("/counter-reports")).willReturn(aResponse().withStatus(500)));
    Async async = context.async();
    async.complete();
    harvester
        .getValidMonths("providerId", "JR1", YearMonth.of(2017, 12), YearMonth.of(2018, 2))
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                context.fail(ar.cause());
              } else {
                assertThat(ar.cause().getMessage()).contains("Received status code", "500");
                async.complete();
              }
            });
  }

  @Test
  public void testGetFetchListHarvestingNotActive(TestContext context) {
    UsageDataProvider provider =
        new UsageDataProvider()
            .withHarvestingConfig(
                new HarvestingConfig().withHarvestingStatus(HarvestingStatus.INACTIVE));

    Async async = context.async();
    harvester
        .getFetchList(provider)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                context.fail();
              } else {
                assertThat(ar.cause()).hasMessageContaining("not active");
                async.complete();
              }
            });
  }

  @Test
  public void testGetFetchList(TestContext context) {
    UsageDataProvider provider = createSampleUsageDataProvider();
    provider.getHarvestingConfig().setHarvestingEnd("2018-03");

    stubFor(
        get(urlPathEqualTo("/counter-reports"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(Json.encodePrettily(createCounterSampleReports()))));

    Async async = context.async();
    harvester
        .getFetchList(provider)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                System.out.println(ar.result());
                assertThat(ar.result().size()).isEqualTo(3);
                final String begin = "2018-03-01";
                final String end = "2018-03-31";
                assertThat(ar.result().contains(new FetchItem("JR1", begin, end))).isTrue();
                assertThat(ar.result().contains(new FetchItem("JR2", begin, end))).isTrue();
                assertThat(ar.result().contains(new FetchItem("JR3", begin, end))).isTrue();
                verify(exactly(3), getRequestedFor(urlPathEqualTo("/counter-reports")));
                async.complete();
              } else {
                context.fail(ar.cause());
              }
            });
  }

  @Test
  public void testFetchAndPostReports(TestContext context) {
    UsageDataProvider provider = createSampleUsageDataProvider();

    stubFor(
        get(urlPathEqualTo("/counter-reports"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(Json.encodePrettily(createCounterSampleReports()))));
    stubFor(
        get(urlEqualTo(
                "/counter-reports?query=%28providerId%3D97329ea7-f351-458a-a460-71aa6db75e35%20"
                    + "AND%20yearMonth%3D2018-03%20AND%20reportName%3DJR1%29&tiny=true"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        Json.encodePrettily(
                            new CounterReports()
                                .withCounterReports(
                                    Collections.singletonList(new CounterReport()))))));
    stubFor(
        get(urlEqualTo(
                "/counter-reports?query=%28providerId%3D97329ea7-f351-458a-a460-71aa6db75e35%20"
                    + "AND%20yearMonth%3D2018-03%20AND%20reportName%3DJR2%29&tiny=true"))
            .willReturn(
                aResponse().withStatus(200).withBody(Json.encodePrettily(new CounterReports()))));
    stubFor(post(urlPathEqualTo("/counter-reports")).willReturn(aResponse().withStatus(201)));
    stubFor(put(urlPathMatching("/counter-reports/.*")).willReturn(aResponse().withStatus(204)));

    Async async = context.async();
    harvester
        .fetchAndPostReports(provider)
        .compose(CompositeFuture::join)
        .setHandler(
            ar -> {
              if (ar.succeeded()) {
                verify(9, getRequestedFor(urlPathEqualTo("/counter-reports")));
                verify(1, postRequestedFor(urlPathEqualTo("/counter-reports")));
                verify(1, putRequestedFor(urlPathMatching("/counter-reports/.*")));
                async.complete();
              } else {
                context.fail(ar.cause());
              }
            });
  }

  private UsageDataProvider createSampleUsageDataProvider() {
    String uuid = "97329ea7-f351-458a-a460-71aa6db75e35";
    return new UsageDataProvider()
        .withId(uuid)
        .withLabel("TestProvider")
        .withVendor(new Vendor().withId("91a11966-548c-4a68-bc02-dc5417635a16"))
        .withPlatform(new Platform().withId("e34430b6-5d13-4c76-99c4-d17ba7c5b5a4"))
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
}
