package org.olf.erm.usage.harvester.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.client.MgrClientImpl.PATH_TENANTS;

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
public class MgrClientImplTest {

  @Rule public WireMockRule teClientRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule public WireMockRule tmClientRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule public Timeout timeoutRule = Timeout.seconds(5);

  private static Vertx vertx;
  private MgrClient client;

  private static final String TENANT_A_ID = "a0000000-0000-0000-0000-000000000001";
  private static final String TENANT_B_ID = "b0000000-0000-0000-0000-000000000002";

  private static final String ENTITLEMENTS_RESPONSE =
      """
      {
        "totalRecords": 2,
        "entitlements": [
          { "applicationId": "app-1.0.0", "tenantId": "%s" },
          { "applicationId": "app-1.0.0", "tenantId": "%s" }
        ]
      }\
      """
          .formatted(TENANT_A_ID, TENANT_B_ID);

  private static final String TENANTS_RESPONSE =
      """
      {
        "totalRecords": 3,
        "tenants": [
          { "id": "%s", "name": "diku" },
          { "id": "%s", "name": "other" },
          { "id": "c0000000-0000-0000-0000-000000000003", "name": "unrelated" }
        ]
      }\
      """
          .formatted(TENANT_A_ID, TENANT_B_ID);

  @Before
  public void setup() {
    vertx = Vertx.vertx();
    JsonObject cfg =
        new JsonObject()
            .put("teClientUrl", teClientRule.baseUrl())
            .put("tmClientUrl", tmClientRule.baseUrl());
    client = new MgrClientImpl(WebClient.create(vertx), cfg);
  }

  @After
  public void after() {
    vertx.close();
  }

  private void stubEntitlements(String body) {
    teClientRule.stubFor(
        get(urlPathMatching("/entitlements/modules/.*"))
            .willReturn(aResponse().withStatus(200).withBody(body)));
  }

  private void stubTenants(String body) {
    tmClientRule.stubFor(
        get(urlPathEqualTo(PATH_TENANTS)).willReturn(aResponse().withStatus(200).withBody(body)));
  }

  @Test
  public void getTenantsValid(TestContext context) {
    stubEntitlements(ENTITLEMENTS_RESPONSE);
    stubTenants(TENANTS_RESPONSE);

    client
        .getEntitledTenantNames()
        .onComplete(
            context.asyncAssertSuccess(
                result ->
                    assertThat(result).hasSize(2).containsExactlyInAnyOrder("diku", "other")));
  }

  @Test
  public void getTenantsEntitlementsBodyInvalid(TestContext context) {
    stubEntitlements("not json");

    client
        .getEntitledTenantNames()
        .onComplete(
            context.asyncAssertFailure(
                cause -> assertThat(cause.getMessage()).contains("Error decoding")));
  }

  @Test
  public void getTenantsEntitlementsEmpty(TestContext context) {
    stubEntitlements(
        """
        { "totalRecords": 0, "entitlements": [] }\
        """);
    stubTenants(TENANTS_RESPONSE);

    client
        .getEntitledTenantNames()
        .onComplete(
            context.asyncAssertSuccess(
                result -> {
                  assertThat(result).isEmpty();
                  tmClientRule.verify(0, getRequestedFor(urlPathEqualTo(PATH_TENANTS)));
                }));
  }

  @Test
  public void getTenantsEntitlements404(TestContext context) {
    teClientRule.stubFor(
        get(urlPathMatching("/entitlements/modules/.*")).willReturn(aResponse().withStatus(404)));

    client
        .getEntitledTenantNames()
        .onComplete(
            context.asyncAssertFailure(cause -> assertThat(cause.getMessage()).contains("404")));
  }

  @Test
  public void getTenantsTenants404(TestContext context) {
    stubEntitlements(ENTITLEMENTS_RESPONSE);
    tmClientRule.stubFor(get(urlPathEqualTo(PATH_TENANTS)).willReturn(aResponse().withStatus(404)));

    client
        .getEntitledTenantNames()
        .onComplete(
            context.asyncAssertFailure(cause -> assertThat(cause.getMessage()).contains("404")));
  }

  @Test
  public void getTenantsNoService(TestContext context) {
    teClientRule.stop();

    client.getEntitledTenantNames().onComplete(context.asyncAssertFailure());
  }

  @Test
  public void getTenantsWithFault(TestContext context) {
    teClientRule.stubFor(
        get(urlPathMatching("/entitlements/modules/.*"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    client.getEntitledTenantNames().onComplete(context.asyncAssertFailure());
  }

  @Test
  public void getTenantsUrlsNotConfigured(TestContext context) {
    MgrClient unconfiguredClient = new MgrClientImpl(WebClient.create(vertx), new JsonObject());

    unconfiguredClient
        .getEntitledTenantNames()
        .onComplete(
            context.asyncAssertFailure(
                cause ->
                    assertThat(cause.getMessage()).contains("TE_CLIENT_URL and TM_CLIENT_URL")));
  }
}
