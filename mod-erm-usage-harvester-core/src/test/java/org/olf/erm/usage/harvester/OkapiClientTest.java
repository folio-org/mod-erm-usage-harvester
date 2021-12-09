package org.olf.erm.usage.harvester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(VertxUnitRunner.class)
public class OkapiClientTest {

  private static final Logger LOG = LoggerFactory.getLogger(OkapiClientTest.class);

  @Rule public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule public Timeout timeoutRule = Timeout.seconds(5);

  private static final String tenantId = "diku";

  private static final String deployCfg =
      "{\n"
          + "  \"okapiUrl\": \"http://localhost\",\n"
          + "  \"tenantsPath\": \"/_/proxy/tenants\",\n"
          + "  \"reportsPath\": \"/counter-reports\",\n"
          + "  \"providerPath\": \"/usage-data-providers\",\n"
          + "  \"aggregatorPath\": \"/aggregator-settings\"\n"
          + "}";

  private static Vertx vertx;
  private String tenantsPath;
  private OkapiClient okapiClient;

  @Before
  public void setup() {
    vertx = Vertx.vertx();
    JsonObject cfg = new JsonObject(deployCfg);
    cfg.put("okapiUrl", StringUtils.removeEnd(wireMockRule.url(""), "/"));
    cfg.put("testing", true);
    this.tenantsPath = cfg.getString("tenantsPath");
    okapiClient = new OkapiClient(WebClient.create(vertx), cfg);
  }

  @After
  public void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void getTenantsBodyValid(TestContext context) {
    stubFor(
        get(urlEqualTo(tenantsPath))
            .willReturn(aResponse().withBodyFile("TenantsResponse200.json")));

    Async async = context.async();
    okapiClient
        .getTenants()
        .onComplete(
            ar -> {
              context.assertTrue(ar.succeeded());
              context.assertEquals(2, ar.result().size());
              context.assertEquals(tenantId, ar.result().get(0));
              async.complete();
            });
  }

  @Test
  public void getTenantsBodyInvalid(TestContext context) {
    stubFor(get(urlEqualTo(tenantsPath)).willReturn(aResponse().withBody("{ }")));

    Async async = context.async();
    okapiClient
        .getTenants()
        .onComplete(
            ar -> {
              context.assertTrue(ar.failed());
              context.assertTrue(ar.cause().getMessage().contains("Error decoding"));
              async.complete();
            });
  }

  @Test
  public void getTenantsBodyEmpty(TestContext context) {
    stubFor(get(urlEqualTo(tenantsPath)).willReturn(aResponse().withBody("[ ]")));

    Async async = context.async();
    okapiClient
        .getTenants()
        .onComplete(
            ar -> {
              context.assertTrue(ar.succeeded());
              context.assertTrue(ar.result().isEmpty());
              async.complete();
            });
  }

  @Test
  public void getTenantsResponseInvalid(TestContext context) {
    stubFor(get(urlEqualTo(tenantsPath)).willReturn(aResponse().withStatus(404)));

    Async async = context.async();
    okapiClient
        .getTenants()
        .onComplete(
            ar -> {
              context.assertTrue(ar.failed());
              context.assertTrue(ar.cause().getMessage().contains("404"));
              async.complete();
            });
  }

  @Test
  public void getTenantsNoService(TestContext context) {
    wireMockRule.stop();

    Async async = context.async();
    okapiClient
        .getTenants()
        .onComplete(
            ar -> {
              context.assertTrue(ar.failed());
              LOG.error(ar.cause().getMessage(), ar.cause());
              async.complete();
            });
  }

  @Test
  public void getTenantsWithFault(TestContext context) {
    stubFor(
        get(urlEqualTo(tenantsPath))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    Async async = context.async();
    okapiClient
        .getTenants()
        .onComplete(
            ar -> {
              context.assertTrue(ar.failed());
              async.complete();
            });
  }
}
