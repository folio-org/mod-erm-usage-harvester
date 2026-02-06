package org.olf.erm.usage.harvester.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.client.ExtAggregatorSettingsClientImpl.PATH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import java.io.IOException;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ExtAggregatorSettingsClientImplTest {

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(new WireMockConfiguration().dynamicPort());

  private ExtAggregatorSettingsClient aggregatorSettingsClient;

  @Before
  public void setUp() {
    aggregatorSettingsClient =
        new ExtAggregatorSettingsClientImpl(
            wireMockRule.baseUrl(), "someTenant", "someToken", WebClient.create(Vertx.vertx()));
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
        get(urlEqualTo(PATH + "/" + provider.getHarvestingConfig().getAggregator().getId()))
            .willReturn(aResponse().withBodyFile("aggregator-setting.json")));

    aggregatorSettingsClient
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
    aggregatorSettingsClient
        .getAggregatorSetting(provider1)
        .onComplete(
            ar -> {
              context.verify(
                  v -> {
                    assertThat(ar.failed()).isTrue();
                    assertThat(ar.cause().getMessage()).contains("No aggregator present");
                  });
              async.complete();
            });

    provider2.getHarvestingConfig().getAggregator().setId(null);
    Async async2 = context.async();
    aggregatorSettingsClient
        .getAggregatorSetting(provider2)
        .onComplete(
            ar -> {
              context.verify(
                  v -> {
                    assertThat(ar.failed()).isTrue();
                    assertThat(ar.cause().getMessage()).contains("No aggregator present");
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
        get(urlEqualTo(PATH + "/" + provider.getHarvestingConfig().getAggregator().getId()))
            .willReturn(aResponse().withBody("garbage")));

    aggregatorSettingsClient
        .getAggregatorSetting(provider)
        .onComplete(context.asyncAssertFailure());
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
        get(urlEqualTo(PATH + "/" + provider.getHarvestingConfig().getAggregator().getId()))
            .willReturn(
                aResponse().withBody("Aggregator settingObject does not exist").withStatus(404)));

    aggregatorSettingsClient
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
    wireMockRule.stop();

    aggregatorSettingsClient
        .getAggregatorSetting(provider)
        .onComplete(context.asyncAssertFailure());
  }
}
