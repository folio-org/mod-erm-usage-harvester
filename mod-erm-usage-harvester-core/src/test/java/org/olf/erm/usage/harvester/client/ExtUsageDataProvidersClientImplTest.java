package org.olf.erm.usage.harvester.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.client.ExtUsageDataProvidersClientImpl.PATH;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.time.Instant;
import java.util.Date;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ExtUsageDataProvidersClientImplTest {

  private static final String UDP_ID = "b4aca312-f769-4988-8040-b31234679f7b";
  private static final String UDP_PATH_WITH_ID = PATH + "/" + UDP_ID;
  private static final Date DATE_NOW = Date.from(Instant.now());

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(new WireMockConfiguration().dynamicPort());

  private ExtUsageDataProvidersClient udpClient;

  @Before
  public void setUp() {
    udpClient =
        new ExtUsageDataProvidersClientImpl(wireMockRule.baseUrl(), "someTenant", "someToken");
  }

  @Test
  public void testUpdateUDPLastHarvestingDateSuccess(TestContext context) {
    stubFor(put(UDP_PATH_WITH_ID).willReturn(aResponse().withStatus(204)));

    udpClient
        .updateUDPLastHarvestingDate(new UsageDataProvider().withId(UDP_ID), DATE_NOW)
        .onComplete(
            context.asyncAssertSuccess(
                v ->
                    verify(
                        1,
                        putRequestedFor(urlEqualTo(UDP_PATH_WITH_ID))
                            .withRequestBody(
                                matchingJsonPath(
                                    "$.harvestingDate",
                                    equalTo(String.valueOf(DATE_NOW.getTime())))))));
  }

  @Test
  public void testUpdateUDPLastHarvestingDateFail(TestContext context) {
    stubFor(
        put(UDP_PATH_WITH_ID).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    udpClient
        .updateUDPLastHarvestingDate(new UsageDataProvider().withId(UDP_ID), DATE_NOW)
        .onComplete(
            context.asyncAssertFailure(
                t -> assertThat(t).hasMessageContaining("Failed updating harvestingDate")));
  }

  @Test
  public void testUpdateUDPLastHarvestingDateWrongStatusCode(TestContext context) {
    stubFor(put(UDP_PATH_WITH_ID).willReturn(aResponse().withStatus(400)));
    udpClient
        .updateUDPLastHarvestingDate(new UsageDataProvider().withId(UDP_ID), DATE_NOW)
        .onComplete(
            context.asyncAssertFailure(
                t ->
                    assertThat(t)
                        .hasMessageContaining("Failed updating harvestingDate")
                        .hasMessageContaining("400")));
  }

  @Test
  public void getActiveProvidersResponseIsValid(TestContext context) {
    stubFor(
        get(urlPathMatching(PATH))
            .willReturn(aResponse().withBodyFile("usage-data-providers.json")));

    udpClient
        .getActiveProviders()
        .onComplete(
            context.asyncAssertSuccess(udps -> assertThat(udps.getTotalRecords()).isEqualTo(3)));
  }

  @Test
  public void getActiveProvidersResponseBodyIsNull(TestContext context) {
    stubFor(get(urlPathMatching(PATH)).willReturn(aResponse().withBody("")));

    udpClient
        .getActiveProviders()
        .onComplete(
            context.asyncAssertFailure(
                t -> assertThat(t).hasMessageContaining("Response body is null")));
  }

  @Test
  public void getActiveProvidersResponseBodyIsInvalid(TestContext context) {
    stubFor(get(urlPathMatching(PATH)).willReturn(aResponse().withBody("someBody")));

    udpClient.getActiveProviders().onComplete(context.asyncAssertFailure());
  }

  @Test
  public void getActiveProvidersResponseIsInvalid(TestContext context) {
    stubFor(get(urlPathMatching(PATH)).willReturn(aResponse().withStatus(404)));

    udpClient
        .getActiveProviders()
        .onComplete(context.asyncAssertFailure(t -> assertThat(t).hasMessageContaining("404")));
  }

  @Test
  public void getActiveProvidersNoService(TestContext context) {
    wireMockRule.stop();

    udpClient.getActiveProviders().onComplete(context.asyncAssertFailure());
  }
}
