package org.olf.erm.usage.harvester.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class OkapiClientImplTest {

  @Rule public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule public Timeout timeoutRule = Timeout.seconds(5);

  private static Vertx vertx;
  private OkapiClient client;

  @Before
  public void setup() {
    vertx = Vertx.vertx();
    JsonObject cfg = new JsonObject().put("okapiUrl", wireMockRule.baseUrl());
    client = new OkapiClientImpl(WebClient.create(vertx), cfg);
  }

  @After
  public void after() {
    vertx.close();
  }

  private void stubEntitlements(String body) {
    stubFor(
        get(urlPathMatching("/entitlements/modules/.*"))
            .willReturn(aResponse().withStatus(200).withBody(body)));
  }

  @Test
  public void getTenantsValid(TestContext context) {
    stubEntitlements("[\"diku\",\"other\"]");

    client
        .getTenants()
        .onComplete(
            context.asyncAssertSuccess(
                result ->
                    assertThat(result).hasSize(2).containsExactlyInAnyOrder("diku", "other")));
  }

  @Test
  public void getTenantsEmpty(TestContext context) {
    stubEntitlements("[]");

    client
        .getTenants()
        .onComplete(context.asyncAssertSuccess(result -> assertThat(result).isEmpty()));
  }

  @Test
  public void getTenantsBodyInvalid(TestContext context) {
    stubEntitlements("not json");

    client
        .getTenants()
        .onComplete(
            context.asyncAssertFailure(
                cause -> assertThat(cause.getMessage()).contains("Error decoding")));
  }

  @Test
  public void getTenantsBodyNonStringElements(TestContext context) {
    stubEntitlements("[{\"id\":\"diku\"}]");

    client
        .getTenants()
        .onComplete(
            context.asyncAssertFailure(
                cause -> assertThat(cause.getMessage()).contains("Error decoding")));
  }

  @Test
  public void getTenants404(TestContext context) {
    stubFor(
        get(urlPathMatching("/entitlements/modules/.*")).willReturn(aResponse().withStatus(404)));

    client
        .getTenants()
        .onComplete(
            context.asyncAssertFailure(cause -> assertThat(cause.getMessage()).contains("404")));
  }

  @Test
  public void getTenantsNoService(TestContext context) {
    wireMockRule.stop();

    client.getTenants().onComplete(context.asyncAssertFailure());
  }

  @Test
  public void getTenantsWithFault(TestContext context) {
    stubFor(
        get(urlPathMatching("/entitlements/modules/.*"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    client.getTenants().onComplete(context.asyncAssertFailure());
  }

  @Test
  public void getTenantsUrlNotConfigured(TestContext context) {
    OkapiClient unconfiguredClient = new OkapiClientImpl(WebClient.create(vertx), new JsonObject());

    unconfiguredClient
        .getTenants()
        .onComplete(
            context.asyncAssertFailure(
                cause -> assertThat(cause.getMessage()).contains("okapiUrl")));
  }
}
