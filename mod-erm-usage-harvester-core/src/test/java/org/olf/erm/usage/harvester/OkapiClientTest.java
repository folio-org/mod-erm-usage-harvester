package org.olf.erm.usage.harvester;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class OkapiClientTest {

  private static final Logger LOG = Logger.getLogger(OkapiClientTest.class);

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule
  public Timeout timeoutRule = Timeout.seconds(5);

  private static final String tenantId = "diku";

  private static final String deployCfg = "{\n" + "  \"okapiUrl\": \"http://localhost\",\n"
      + "  \"tenantsPath\": \"/_/proxy/tenants\",\n" + "  \"reportsPath\": \"/counter-reports\",\n"
      + "  \"providerPath\": \"/usage-data-providers\",\n"
      + "  \"aggregatorPath\": \"/aggregator-settings\",\n"
      + "  \"moduleIds\": [\"mod-erm-usage-0.0.1\"] \n" + "}";

  private static Vertx vertx;
  private String tenantsPath;
  private List<String> moduleIds;
  private OkapiClient okapiClient;

  @Before
  public void setup(TestContext context) {
    vertx = Vertx.vertx();
    JsonObject cfg = new JsonObject(deployCfg);
    cfg.put("okapiUrl", StringUtils.removeEnd(wireMockRule.url(""), "/"));
    cfg.put("testing", true);
    this.tenantsPath = cfg.getString("tenantsPath");
    this.moduleIds =
        cfg.getJsonArray("moduleIds").stream().map(Object::toString).collect(Collectors.toList());
    okapiClient = new OkapiClient(vertx, cfg);
  }

  @After
  public void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void getTenantsBodyValid(TestContext context) {
    stubFor(get(urlEqualTo(tenantsPath))
        .willReturn(aResponse().withBodyFile("TenantsResponse200.json")));

    Async async = context.async();
    okapiClient.getTenants().setHandler(ar -> {
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
    okapiClient.getTenants().setHandler(ar -> {
      context.assertTrue(ar.failed());
      context.assertTrue(ar.cause().getMessage().contains("Error decoding"));
      async.complete();
    });
  }

  @Test
  public void getTenantsBodyEmpty(TestContext context) {
    stubFor(get(urlEqualTo(tenantsPath)).willReturn(aResponse().withBody("[ ]")));

    Async async = context.async();
    okapiClient.getTenants().setHandler(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertTrue(ar.result().isEmpty());
      async.complete();
    });
  }

  @Test
  public void getTenantsResponseInvalid(TestContext context) {
    stubFor(get(urlEqualTo(tenantsPath)).willReturn(aResponse().withStatus(404)));

    Async async = context.async();
    okapiClient.getTenants().setHandler(ar -> {
      context.assertTrue(ar.failed());
      context.assertTrue(ar.cause().getMessage().contains("404"));
      async.complete();
    });
  }

  @Test
  public void getTenantsNoService(TestContext context) {
    wireMockRule.stop();

    Async async = context.async();
    okapiClient.getTenants().setHandler(ar -> {
      context.assertTrue(ar.failed());
      LOG.error(ar.cause());
      async.complete();
    });
  }

  @Test
  public void getTenantsWithFault(TestContext context) {
    stubFor(get(urlEqualTo(tenantsPath))
        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    Async async = context.async();
    okapiClient.getTenants().setHandler(ar -> {
      context.assertTrue(ar.failed());
      async.complete();
    });
  }

  @Test
  public void hasEnabledModuleNo(TestContext context) {
    stubFor(get(urlEqualTo(tenantsPath + "/" + tenantId + "/modules"))
        .willReturn(aResponse().withBody("[]")));

    Async async = context.async();
    okapiClient.hasEnabledUsageModules(tenantId).setHandler(ar -> {
      context.assertTrue(ar.succeeded());
      context.assertTrue(ar.result() == false);
      async.complete();
    });
  }

  @Test
  public void hasEnabledModuleResponseInvalid(TestContext context) {
    stubFor(get(urlEqualTo(tenantsPath + "/" + tenantId + "/modules"))
        .willReturn(aResponse().withBody("{}")));

    Async async = context.async();
    okapiClient.hasEnabledUsageModules(tenantId).setHandler(ar -> {
      context.assertTrue(ar.failed());
      context.assertTrue(ar.cause().getMessage().contains("Error decoding"));
      async.complete();
    });
  }

  @Test
  public void hasEnabledModuleNoService(TestContext context) {
    wireMockRule.stop();

    Async async = context.async();
    okapiClient.hasEnabledUsageModules(tenantId).setHandler(ar -> {
      context.assertTrue(ar.failed());
      async.complete();
    });
  }

  @Test
  public void hasEnabledModuleYes(TestContext context) {
    JsonArray response = new JsonArray();
    moduleIds.forEach(s -> response.add(new JsonObject().put("id", s)));

    stubFor(get(urlEqualTo(tenantsPath + "/" + tenantId + "/modules"))
        .willReturn(aResponse().withBody(response.toString())));

    Async async = context.async();
    okapiClient.hasEnabledUsageModules(tenantId).setHandler(ar -> {
      context.assertTrue(ar.succeeded());
      async.complete();
    });
  }
}
