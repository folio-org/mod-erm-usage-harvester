package org.olf.erm.usage.harvester.endpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class CS41ImplTest {

  public static final String SUSHI_SERVICE = "/sushiService";
  @Rule public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

  @Test
  public void fetchSingleReport(TestContext ctx) {
    UsageDataProvider provider =
        new UsageDataProvider()
            .withId("67339c41-a3a7-4d19-83e2-c808ab99c8fe")
            .withHarvestingConfig(
                new HarvestingConfig()
                    .withReportRelease(4)
                    .withSushiConfig(
                        new SushiConfig()
                            .withServiceUrl(
                                "http://localhost:" + wireMockRule.port() + SUSHI_SERVICE)))
            .withSushiCredentials(
                new SushiCredentials().withRequestorId("reqId1").withCustomerId("custId1"));

    CS41Impl cs41 = new CS41Impl(provider);

    stubFor(
        post(urlPathEqualTo(SUSHI_SERVICE))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    // .withBodyFile("response1.xml")
                    .withBodyFile("response1.xml")));

    Async async = ctx.async();
    cs41.fetchSingleReport("JR1", "2018-01-01", "2018-01-31")
        .setHandler(
            ar -> {
              assertThat(ar.succeeded()).isTrue();
              verify(1, postRequestedFor(urlPathEqualTo(SUSHI_SERVICE)));

              async.complete();
            });

    async.await(5000);
    wireMockRule.stop();

    Async async2 = ctx.async();
    cs41.fetchSingleReport("JR1", "2018-01-01", "2018-01-31")
        .setHandler(
            ar -> {
              assertThat(ar.failed()).isTrue();
              assertThat(ar.cause().getMessage()).contains("Error getting report");
              async2.complete();
            });
  }
}
