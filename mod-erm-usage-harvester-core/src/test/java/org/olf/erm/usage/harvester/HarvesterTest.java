package org.olf.erm.usage.harvester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.time.YearMonth;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class HarvesterTest {

  private static final Logger LOG = Logger.getLogger(HarvesterTest.class);

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule
  public Timeout timeoutRule = Timeout.seconds(5);

  private static final String tenantId = "diku";
  private static final Token token =
      Token.createDummy(tenantId, "6bf2a318-17a9-4fd9-a889-8baf665ab3c8", tenantId);
  private static final WorkerVerticle harvester = new WorkerVerticle(token);


  private final static JsonObject crJson = new JsonObject("{\n" + "  \"yearMonth\" : \"2016-03\",\n"
      + "  \"reportName\" : \"JR1\",\n" + "  \"platformId\" : \"uuid-123456789\",\n"
      + "  \"customerId\" : \"12345def\",\n" + "  \"release\" : 4,\n" + "  \"format\" : \"???\",\n"
      + "  \"downloadTime\" : \"2018-08-01T15:04:05.967\",\n"
      + "  \"creationTime\" : \"2018-08-01T15:04:06.539\",\n"
      + "  \"vendorId\" : \"uuid-123456789\",\n" + "  \"report\" : \"reportdata\","
      + "  \"id\" : \"d90bc588-1c7c-4b0c-879c-6e3f6c87c3a6\"\n" + "}");

  private static final String deployCfg = "{\n" + "  \"okapiUrl\": \"http://localhost\",\n"
      + "  \"tenantsPath\": \"/_/proxy/tenants\",\n" + "  \"reportsPath\": \"/counter-reports\",\n"
      + "  \"providerPath\": \"/usage-data-providers\",\n"
      + "  \"aggregatorPath\": \"/aggregator-settings\",\n"
      + "  \"moduleId\": \"mod-erm-usage-0.0.1\"\n" + "}";

  private static Vertx vertx;
  private String okapiUrl;
  private String tenantsPath;
  private String reportsPath;
  private String providerPath;
  private String aggregatorPath;
  private String moduleId;

  @Before
  public void setup(TestContext context) {
    vertx = Vertx.vertx();
    JsonObject cfg = new JsonObject(deployCfg);
    cfg.put("okapiUrl", StringUtils.removeEnd(wireMockRule.url(""), "/"));
    cfg.put("testing", true);
    vertx.deployVerticle(harvester, new DeploymentOptions().setConfig(cfg),
        context.asyncAssertSuccess(h -> {
          okapiUrl = harvester.config().getString("okapiUrl");
          tenantsPath = harvester.config().getString("tenantsPath");
          reportsPath = harvester.config().getString("reportsPath");
          providerPath = harvester.config().getString("providerPath");
          aggregatorPath = harvester.config().getString("aggregatorPath");
          moduleId = harvester.config().getString("moduleId");
        }));
  }

  @After
  public void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void getProvidersBodyValid(TestContext context) {
    stubFor(get(urlPathMatching(providerPath))
        .willReturn(aResponse().withBodyFile("usage-data-providers.json")));

    Async async = context.async();
    harvester.getActiveProviders().setHandler(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertEquals(3, ar.result().getTotalRecords());
      async.complete();
    });
  }

  @Test
  public void getProvidersBodyInvalid(TestContext context) {
    stubFor(get(urlPathMatching(providerPath)).willReturn(aResponse().withBody("")));

    Async async = context.async();
    harvester.getActiveProviders().setHandler(ar -> {
      context.assertTrue(ar.failed());
      context.assertTrue(ar.cause().getMessage().contains("Error decoding"));
      async.complete();
    });
  }

  @Test
  public void getProvidersResponseInvalid(TestContext context) {
    stubFor(get(urlPathMatching(providerPath)).willReturn(aResponse().withStatus(404)));

    Async async = context.async();
    harvester.getActiveProviders().setHandler(ar -> {
      context.assertTrue(ar.failed());
      context.assertTrue(ar.cause().getMessage().contains("404"));
      async.complete();
    });
  }

  @Test
  public void getProvidersNoService(TestContext context) {
    wireMockRule.stop();

    Async async = context.async();
    harvester.getActiveProviders().setHandler(ar -> {
      context.assertTrue(ar.failed());
      LOG.error(ar.cause());
      async.complete();
    });
  }

  @Test
  public void getAggregatorSettingsBodyValid(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {
    final UsageDataProvider provider = new ObjectMapper().readValue(Resources
        .toString(Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
        UsageDataProvider.class);
    stubFor(get(urlEqualTo(aggregatorPath + "/" + provider.getAggregator().getId()))
        .willReturn(aResponse().withBodyFile("aggregator-setting.json")));

    Async async = context.async();
    harvester.getAggregatorSetting(provider).setHandler(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertTrue("Nationaler Statistikserver".equals(ar.result().getLabel()));
      async.complete();
    });
  }

  @Test
  public void getAggregatorSettingsBodyValidNoAggregator(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {
    final UsageDataProvider provider1 = new ObjectMapper().readValue(Resources
        .toString(Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
        UsageDataProvider.class);
    final UsageDataProvider provider2 = new ObjectMapper().readValue(Resources
        .toString(Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
        UsageDataProvider.class);

    provider1.setAggregator(null);
    Async async = context.async();
    harvester.getAggregatorSetting(provider1).setHandler(ar -> {
      context.assertTrue(ar.failed());
      context.assertTrue(ar.result() == null);
      context.assertTrue(ar.cause().getMessage().contains("no aggregator found"));
      async.complete();
    });

    provider2.getAggregator().setId(null);
    Async async2 = context.async();
    harvester.getAggregatorSetting(provider2).setHandler(ar -> {
      context.assertTrue(ar.failed());
      context.assertTrue(ar.result() == null);
      context.assertTrue(ar.cause().getMessage().contains("no aggregator found"));
      async2.complete();
    });
  }

  @Test
  public void getAggregatorSettingsBodyInvalid(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {
    final UsageDataProvider provider = new ObjectMapper().readValue(Resources
        .toString(Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
        UsageDataProvider.class);
    stubFor(get(urlEqualTo(aggregatorPath + "/" + provider.getAggregator().getId()))
        .willReturn(aResponse().withBody("garbage")));

    Async async = context.async();
    harvester.getAggregatorSetting(provider).setHandler(ar -> {
      context.assertTrue(ar.failed());
      context.assertTrue(ar.result() == null);
      context.assertTrue(ar.cause().getMessage().contains("Error decoding"));
      async.complete();
    });
  }

  @Test
  public void getAggregatorSettingsResponseInvalid(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {
    final UsageDataProvider provider = new ObjectMapper().readValue(Resources
        .toString(Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
        UsageDataProvider.class);
    stubFor(get(urlEqualTo(aggregatorPath + "/" + provider.getAggregator().getId())).willReturn(
        aResponse().withBody("Aggregator settingObject does not exist").withStatus(404)));

    Async async = context.async();
    harvester.getAggregatorSetting(provider).setHandler(ar -> {
      context.assertTrue(ar.failed());
      context.assertTrue(ar.result() == null);
      context.assertTrue(ar.cause().getMessage().contains("404"));
      async.complete();
    });
  }

  @Test
  public void getAggregatorSettingsNoService(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {
    final UsageDataProvider provider = new ObjectMapper().readValue(Resources
        .toString(Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
        UsageDataProvider.class);
    wireMockRule.stop();

    Async async = context.async();
    harvester.getAggregatorSetting(provider).setHandler(ar -> {
      context.assertTrue(ar.failed());
      async.complete();
    });
  }

  @Test
  public void createReportJsonObject()
      throws JsonParseException, JsonMappingException, IOException {
    final UsageDataProvider provider = new ObjectMapper().readValue(Resources
        .toString(Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
        UsageDataProvider.class);

    final String reportName = "JR1";
    final String reportData = "testreport";
    final YearMonth yearMonth = YearMonth.of(2018, 01);

    CounterReport result =
        harvester.createCounterReport(reportData, reportName, provider, yearMonth);
    assertTrue(result != null);
    assertEquals(reportName, result.getReportName());
    assertEquals(reportData, result.getReport());
    assertEquals(yearMonth.toString(), result.getYearMonth());
    assertEquals(provider.getPlatformId(), result.getPlatformId());
    assertEquals(provider.getCustomerId(), result.getCustomerId());
  }

  @Test
  public void postReportNoExisting(TestContext context) {
    final String url = reportsPath;
    stubFor(get(urlPathEqualTo(url))
        .willReturn(aResponse().withStatus(200).withBodyFile("counter-reports-empty.json")));
    stubFor(post(urlEqualTo(url)).willReturn(aResponse().withStatus(201)));

    Async async = context.async();
    harvester.postReport(Json.decodeValue(crJson.toString(), CounterReport.class))
        .setHandler(ar -> {
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
    stubFor(get(urlPathEqualTo(url))
        .willReturn(aResponse().withStatus(200).withBodyFile("counter-reports-one.json")));
    stubFor(put(urlEqualTo(urlId)).willReturn(aResponse().withStatus(201)));

    Async async = context.async();
    harvester.postReport(Json.decodeValue(crJson.toString(), CounterReport.class))
        .setHandler(ar -> {
          if (ar.succeeded()) {
            wireMockRule.verify(putRequestedFor(urlEqualTo(urlId)));
            async.complete();
          } else {
            context.fail(ar.cause());
          }
        });
  }

  @Test
  public void getServiceEndpoint(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {
    final UsageDataProvider provider = new ObjectMapper().readValue(Resources
        .toString(Resources.getResource("__files/usage-data-provider.json"), Charsets.UTF_8),
        UsageDataProvider.class);

    stubFor(get(urlEqualTo(aggregatorPath + "/" + provider.getAggregator().getId()))
        .willReturn(aResponse().withBodyFile("aggregator-setting.json")));

    Async async = context.async();
    harvester.getServiceEndpoint(provider).setHandler(ar -> {
      if (ar.succeeded()) {
        context.assertTrue(ar.result() != null);
        async.complete();
      } else {
        context.fail();
      }
    });
  }
}
