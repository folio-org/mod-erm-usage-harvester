package org.olf.erm.usage.harvester.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.List;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configs;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ExtConfigurationsClientTest {

  private static final String MOD_CONFIGURATION_PATH = "/configurations/entries";

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(new WireMockConfiguration().dynamicPort());

  private ExtConfigurationsClient configurationsClient;

  @Before
  public void setUp() {
    configurationsClient =
        new ExtConfigurationsClient(wireMockRule.baseUrl(), "someTenant", "someToken");
  }

  @Test
  public void testGetModConfigurationValueResponseOk(TestContext context) {
    Configs configs =
        new Configs()
            .withTotalRecords(1)
            .withConfigs(
                List.of(
                    new Config().withModule("testmodule").withConfigName("test").withValue("5")));
    stubFor(
        get(urlPathEqualTo(MOD_CONFIGURATION_PATH))
            .withQueryParam("query", equalTo("(module = testmodule and configName = test)"))
            .willReturn(aResponse().withStatus(200).withBody(Json.encode(configs))));

    configurationsClient
        .getModConfigurationValue("testmodule", "test")
        .onComplete(context.asyncAssertSuccess(s -> assertThat(s).isEqualTo("5")));
  }

  @Test
  public void testGetModConfigurationValueResponseNoEntry(TestContext context) {
    stubFor(
        get(urlPathEqualTo(MOD_CONFIGURATION_PATH))
            .withQueryParam("query", equalTo("(module = testmodule and configName = test)"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(Json.encode(new Configs().withTotalRecords(0)))));

    configurationsClient
        .getModConfigurationValue("testmodule", "test")
        .onComplete(context.asyncAssertFailure());
  }

  @Test
  public void testGetModConfigurationValueResponseNull(TestContext context) {
    stubFor(
        get(urlPathEqualTo(MOD_CONFIGURATION_PATH))
            .withQueryParam("query", equalTo("(module = testmodule and configName = test)"))
            .willReturn(aResponse().withStatus(200).withBody("")));

    configurationsClient
        .getModConfigurationValue("testmodule", "test")
        .onComplete(context.asyncAssertFailure());
  }

  @Test
  public void testGetModConfigurationValueResponse404(TestContext context) {
    stubFor(
        get(urlPathEqualTo(MOD_CONFIGURATION_PATH))
            .withQueryParam("query", equalTo("(module = testmodule and configName = test)"))
            .willReturn(aResponse().withStatus(404)));

    configurationsClient
        .getModConfigurationValue("testmodule", "test")
        .onComplete(context.asyncAssertFailure());
  }

  @Test
  public void testGetModConfigurationValueResponseNoService(TestContext context) {
    wireMockRule.stop();
    configurationsClient
        .getModConfigurationValue("testmodule", "test")
        .onComplete(context.asyncAssertFailure());
  }
}
