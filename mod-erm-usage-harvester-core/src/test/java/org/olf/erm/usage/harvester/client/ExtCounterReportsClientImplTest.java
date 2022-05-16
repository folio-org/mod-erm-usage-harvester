package org.olf.erm.usage.harvester.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.TestUtil.createSampleUsageDataProvider;
import static org.olf.erm.usage.harvester.client.ExtCounterReportsClientImpl.PATH;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.CounterReports;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestingStatus;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.FetchItem;

@RunWith(VertxUnitRunner.class)
public class ExtCounterReportsClientImplTest {

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(new WireMockConfiguration().dynamicPort());

  private static CounterReport cr;
  private ExtCounterReportsClient counterReportsClient;

  @BeforeClass
  public static void beforeClass() throws IOException {
    final String str =
        Resources.toString(Resources.getResource("counterreport-sample.json"), Charsets.UTF_8);
    cr = Json.decodeValue(str, CounterReport.class);
  }

  @Before
  public void setUp() {
    counterReportsClient =
        new ExtCounterReportsClientImpl(wireMockRule.baseUrl(), "someTenant", "someToken");
  }

  private CounterReports createCounterSampleReports() {
    UUID uuid = UUID.randomUUID();
    List<CounterReport> reports =
        Stream.iterate(YearMonth.of(2017, 12), m -> m.plusMonths(1))
            .limit(3)
            .map(
                m ->
                    new CounterReport()
                        .withReport(new Report())
                        .withProviderId(uuid.toString())
                        .withYearMonth(m.toString()))
            .collect(Collectors.toList());
    return new CounterReports().withCounterReports(reports);
  }

  @Test
  public void postReportNoExisting(TestContext context) {
    stubFor(
        get(urlPathEqualTo(PATH))
            .willReturn(aResponse().withStatus(200).withBodyFile("counter-reports-empty.json")));
    stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(201)));

    counterReportsClient
        .upsertReport(cr)
        .onComplete(context.asyncAssertSuccess(v -> verify(postRequestedFor(urlEqualTo(PATH)))));
  }

  @Test
  public void postReportExisting(TestContext context) {
    final String urlId = PATH + "/43d7e87c-fb32-4ce2-81f9-11fe75c29bbb";
    stubFor(
        get(urlPathEqualTo(PATH))
            .willReturn(aResponse().withStatus(200).withBodyFile("counter-reports-one.json")));
    stubFor(put(urlEqualTo(urlId)).willReturn(aResponse().withStatus(201)));

    counterReportsClient
        .upsertReport(cr)
        .onComplete(context.asyncAssertSuccess(v -> verify(putRequestedFor(urlEqualTo(urlId)))));
  }

  @Test
  public void testGetValidMonths(TestContext context) {
    String encode = Json.encodePrettily(createCounterSampleReports());
    stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody(encode)));

    counterReportsClient
        .getValidMonths("providerId", "JR1", YearMonth.of(2017, 12), YearMonth.of(2018, 2), 5)
        .onComplete(
            context.asyncAssertSuccess(
                list ->
                    assertThat(list)
                        .isEqualTo(
                            Arrays.asList(
                                YearMonth.of(2017, 12),
                                YearMonth.of(2018, 1),
                                YearMonth.of(2018, 2)))));
  }

  @Test
  public void testGetValidMonthsFail(TestContext context) {
    stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));
    counterReportsClient
        .getValidMonths("providerId", "JR1", YearMonth.of(2017, 12), YearMonth.of(2018, 2), 5)
        .onComplete(
            context.asyncAssertFailure(
                t ->
                    assertThat(t)
                        .hasMessageContaining("Received status code")
                        .hasMessageContaining("500")));
  }

  @Test
  public void testGetFetchListHarvestingNotActive(TestContext context) {
    UsageDataProvider provider =
        new UsageDataProvider()
            .withHarvestingConfig(
                new HarvestingConfig().withHarvestingStatus(HarvestingStatus.INACTIVE));

    counterReportsClient
        .getFetchList(provider, 5)
        .onComplete(
            context.asyncAssertFailure(t -> assertThat(t).hasMessageContaining("not active")));
  }

  @Test
  public void testGetFetchList(TestContext context) {
    UsageDataProvider provider = createSampleUsageDataProvider();
    provider.getHarvestingConfig().setHarvestingEnd("2018-03");

    stubFor(
        get(urlPathEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(Json.encodePrettily(createCounterSampleReports()))));

    counterReportsClient
        .getFetchList(provider, 5)
        .onComplete(
            context.asyncAssertSuccess(
                list -> {
                  final String begin = "2018-03-01";
                  final String end = "2018-03-31";
                  assertThat(list)
                      .hasSize(3)
                      .containsExactlyInAnyOrder(
                          new FetchItem("JR1", begin, end),
                          new FetchItem("JR2", begin, end),
                          new FetchItem("JR3", begin, end));
                  verify(exactly(3), getRequestedFor(urlPathEqualTo("/counter-reports")));
                }));
  }
}
