package org.olf.erm.usage.harvester.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.client.OkapiClientImpl.MSG_SYSTEM_USER_LOGIN_DISABLED;
import static org.olf.erm.usage.harvester.client.OkapiClientImpl.PATH_LOGIN;
import static org.olf.erm.usage.harvester.client.OkapiClientImpl.PATH_LOGIN_EXPIRY;
import static org.olf.erm.usage.harvester.client.OkapiClientImpl.PATH_TENANTS;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import org.apache.commons.lang3.StringUtils;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.SystemUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(VertxUnitRunner.class)
public class OkapiClientImplTest {

  private static final Logger LOG = LoggerFactory.getLogger(OkapiClientImplTest.class);
  private static final SystemUser SYSTEM_USER = new SystemUser("user", "pass");

  @Rule public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule public Timeout timeoutRule = Timeout.seconds(5);

  private static final String tenantId = "diku";
  private static Vertx vertx;
  private OkapiClient okapiClient;
  private String okapiUrl;

  @Before
  public void setup() {
    vertx = Vertx.vertx();
    JsonObject cfg = new JsonObject();
    okapiUrl = StringUtils.removeEnd(wireMockRule.url(""), "/");
    cfg.put("okapiUrl", okapiUrl);
    okapiClient = new OkapiClientImpl(WebClient.create(vertx), cfg);
  }

  @After
  public void after() {
    vertx.close();
  }

  @Test
  public void testLegacyLoginSuccess(TestContext context) {
    stubFor(post(urlEqualTo(PATH_LOGIN_EXPIRY)).willReturn(aResponse().withStatus(404)));
    stubFor(
        post(urlEqualTo(PATH_LOGIN))
            .willReturn(aResponse().withStatus(201).withHeader(XOkapiHeaders.TOKEN, "someToken")));
    okapiClient
        .loginSystemUser(tenantId, SYSTEM_USER)
        .onComplete(context.asyncAssertSuccess(s -> assertThat(s).isEqualTo("someToken")));
  }

  @Test
  public void testLoginWithExpirySuccess(TestContext context) {
    stubFor(
        post(urlEqualTo(PATH_LOGIN_EXPIRY))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Set-Cookie", "folioAccessToken=expiryToken")));
    stubFor(post(urlEqualTo(PATH_LOGIN)).willReturn(aResponse().withStatus(404)));
    okapiClient
        .loginSystemUser(tenantId, SYSTEM_USER)
        .onComplete(context.asyncAssertSuccess(s -> assertThat(s).isEqualTo("expiryToken")));
  }

  @Test
  public void testLegacyLoginFailure(TestContext context) {
    stubFor(post(urlEqualTo(PATH_LOGIN_EXPIRY)).willReturn(aResponse().withStatus(404)));
    stubFor(post(urlEqualTo(PATH_LOGIN)).willReturn(aResponse().withStatus(422)));
    okapiClient.loginSystemUser(tenantId, SYSTEM_USER).onComplete(context.asyncAssertFailure());
  }

  @Test
  public void testSystemUserDisabled(TestContext context) {
    okapiClient = new OkapiClientImpl(WebClient.create(vertx), okapiUrl, false);
    okapiClient
        .loginSystemUser(tenantId, SYSTEM_USER)
        .onComplete(
            context.asyncAssertFailure(
                t -> assertThat(t).hasMessage(MSG_SYSTEM_USER_LOGIN_DISABLED)));
  }

  @Test
  public void testLoginWithExpiryInvalidCookie(TestContext context) {
    stubFor(
        post(urlEqualTo(PATH_LOGIN_EXPIRY))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Set-Cookie", "folioAccessToken:expiryToken")));
    stubFor(post(urlEqualTo(PATH_LOGIN)).willReturn(aResponse().withStatus(422)));
    okapiClient.loginSystemUser(tenantId, SYSTEM_USER).onComplete(context.asyncAssertFailure());
  }

  @Test
  public void getTenantsBodyValid(TestContext context) {
    stubFor(
        get(urlEqualTo(PATH_TENANTS))
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
    stubFor(get(urlEqualTo(PATH_TENANTS)).willReturn(aResponse().withBody("{ }")));

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
    stubFor(get(urlEqualTo(PATH_TENANTS)).willReturn(aResponse().withBody("[ ]")));

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
    stubFor(get(urlEqualTo(PATH_TENANTS)).willReturn(aResponse().withStatus(404)));

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
        get(urlEqualTo(PATH_TENANTS))
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
