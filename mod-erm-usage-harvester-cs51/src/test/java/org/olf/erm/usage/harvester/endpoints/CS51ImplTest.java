package org.olf.erm.usage.harvester.endpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.olf.erm.usage.harvester.endpoints.CS51Impl.ATTRIBUTES_TO_SHOW_DR;
import static org.olf.erm.usage.harvester.endpoints.CS51Impl.ATTRIBUTES_TO_SHOW_IR;
import static org.olf.erm.usage.harvester.endpoints.CS51Impl.ATTRIBUTES_TO_SHOW_PR;
import static org.olf.erm.usage.harvester.endpoints.CS51Impl.ATTRIBUTES_TO_SHOW_TR;
import static org.olf.erm.usage.harvester.endpoints.InvalidServiceURLException.MSG_INVALID_SERVICE_URL;
import static org.olf.erm.usage.harvester.endpoints.UnsupportedReportTypeException.MSG_UNSUPPORTED_REPORT_TYPE;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.olf.erm.usage.counter51.client.Counter51ClientException;

@ExtendWith(VertxExtension.class)
class CS51ImplTest {

  @RegisterExtension
  private static final WireMockExtension serviceMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String REQUESTOR_ID = "reqId123";
  private static final String API_KEY = "apiKey123";
  private static final String PLATFORM = "platform123";
  private static final String REPORT_RELEASE = "5.1";
  private static final String BASE_PATH = "/r51/reports/";
  private static final String PATH_TR = BASE_PATH + "tr";
  private static final String ATTRIBUTES_TO_SHOW = "attributes_to_show";
  private static final String CUSTOMER_ID = "custId123";
  private static final String BEGIN_DATE = "2022-01";
  private static final String END_DATE = "2022-03";
  private static final String REPORT_TR = "TR";
  private static final String MSG_USAGE_NOT_READY = "Usage Not Ready for Requested Dates";
  private static final String MSG_UNRECOGNIZED_FIELD = "Unrecognized field \"Database\"";
  private static UsageDataProvider provider;

  @BeforeAll
  static void beforeAll() {
    provider =
        new UsageDataProvider()
            .withId("519803f7-4f5e-4224-8eda-9d56ef2af48a")
            .withSushiCredentials(
                new SushiCredentials()
                    .withCustomerId(CUSTOMER_ID)
                    .withRequestorId(REQUESTOR_ID)
                    .withApiKey(API_KEY)
                    .withPlatform(PLATFORM))
            .withHarvestingConfig(
                new HarvestingConfig()
                    .withReportRelease(REPORT_RELEASE)
                    .withSushiConfig(
                        new SushiConfig()
                            .withServiceUrl(serviceMock.getRuntimeInfo().getHttpBaseUrl())));
  }

  private static Stream<Entry<String, Map<String, String>>> testRequestParametersProvider() {
    return Stream.of(
        Map.entry(REPORT_TR, Map.of(ATTRIBUTES_TO_SHOW, ATTRIBUTES_TO_SHOW_TR)),
        Map.entry("PR", Map.of(ATTRIBUTES_TO_SHOW, ATTRIBUTES_TO_SHOW_PR)),
        Map.entry("DR", Map.of(ATTRIBUTES_TO_SHOW, ATTRIBUTES_TO_SHOW_DR)),
        Map.entry(
            "IR",
            Map.of(ATTRIBUTES_TO_SHOW, ATTRIBUTES_TO_SHOW_IR, "include_parent_details", "True")));
  }

  @ParameterizedTest
  @MethodSource("testRequestParametersProvider")
  void testRequestParameters(
      Entry<String, Map<String, String>> params, VertxTestContext testContext) {
    String path = BASE_PATH + params.getKey().toLowerCase();
    serviceMock.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(404)));

    RequestPatternBuilder requestPatternBuilder =
        getRequestedFor(urlPathEqualTo(path))
            .withQueryParam("begin_date", equalTo(BEGIN_DATE))
            .withQueryParam("end_date", equalTo(END_DATE))
            .withQueryParam("customer_id", equalTo(CUSTOMER_ID))
            .withQueryParam("requestor_id", equalTo(REQUESTOR_ID))
            .withQueryParam("api_key", equalTo(API_KEY))
            .withQueryParam("platform", equalTo(PLATFORM));
    params.getValue().forEach((k, v) -> requestPatternBuilder.withQueryParam(k, equalTo(v)));

    new CS51Impl(provider)
        .fetchReport(params.getKey(), BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(() -> serviceMock.verify(1, requestPatternBuilder))
                    .completeNow());
  }

  @Test
  void testUnsupportedReport(VertxTestContext testContext) {
    String reportName = "XY";
    String path = BASE_PATH + reportName.toLowerCase();
    serviceMock.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(404)));

    new CS51Impl(provider)
        .fetchReport(reportName, BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(
                        () -> {
                          serviceMock.verify(0, getRequestedFor(urlPathEqualTo(path)));
                          assertThat(ar.failed()).isTrue();
                          assertThat(ar.cause())
                              .isInstanceOf(UnsupportedReportTypeException.class)
                              .hasMessage(String.format(MSG_UNSUPPORTED_REPORT_TYPE, reportName));
                        })
                    .completeNow());
  }

  @Test
  void testInvalidServiceUrl() {
    String serviceUrlStr = "localhost";
    UsageDataProvider invalidProvider =
        new UsageDataProvider()
            .withSushiCredentials(new SushiCredentials())
            .withHarvestingConfig(
                new HarvestingConfig()
                    .withSushiConfig(new SushiConfig().withServiceUrl(serviceUrlStr)));
    assertThatThrownBy(() -> new CS51Impl(invalidProvider))
        .isInstanceOf(InvalidServiceURLException.class)
        .hasMessage(String.format(MSG_INVALID_SERVICE_URL, serviceUrlStr));
  }

  @Test
  void testReceiveValidReport(VertxTestContext testContext) {
    serviceMock.stubFor(
        get(urlPathEqualTo(PATH_TR))
            .willReturn(aResponse().withStatus(200).withBodyFile("TR.json")));

    new CS51Impl(provider)
        .fetchReport(REPORT_TR, BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(
                        () -> {
                          assertThat(ar.succeeded()).isTrue();
                          assertThat(ar.result())
                              .satisfies(
                                  r -> {
                                    assertThat(r)
                                        .extracting("yearMonth")
                                        .containsExactly("2022-01", "2022-02", "2022-03");
                                    assertThat(r).extracting("release").containsOnly("5.1");
                                    assertThat(r).extracting("reportName").containsOnly("TR");
                                    assertThat(r)
                                        .extracting("providerId")
                                        .containsOnly(provider.getId());
                                    assertThat(r)
                                        .extracting("downloadTime")
                                        .doesNotContainNull()
                                        .allMatch(time -> time.equals(r.get(0).getDownloadTime()));
                                    assertThat(r).extracting("report").doesNotContainNull();
                                    assertThat(r).extracting("failedAttempts").containsOnlyNulls();
                                  });
                        })
                    .completeNow());
  }

  @Test
  void testReceiveValidReportWithException(VertxTestContext testContext) {
    serviceMock.stubFor(
        get(urlPathEqualTo(PATH_TR))
            .willReturn(aResponse().withStatus(200).withBodyFile("TR_with_exception.json")));

    new CS51Impl(provider)
        .fetchReport(REPORT_TR, BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(
                        () -> {
                          assertThat(ar.succeeded()).isFalse();
                          assertThat(ar.cause())
                              .isInstanceOf(InvalidReportException.class)
                              .hasMessageContaining(MSG_USAGE_NOT_READY);
                        })
                    .completeNow());
  }

  @Test
  void testReceiveInvalidReport(VertxTestContext testContext) {
    serviceMock.stubFor(
        get(urlPathEqualTo(PATH_TR))
            .willReturn(aResponse().withStatus(200).withBodyFile("TR_invalid.json")));

    new CS51Impl(provider)
        .fetchReport(REPORT_TR, BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(
                        () -> {
                          assertThat(ar.succeeded()).isFalse();
                          assertThat(ar.cause())
                              .isInstanceOf(Counter51ClientException.class)
                              .hasMessageContaining(MSG_UNRECOGNIZED_FIELD);
                        })
                    .completeNow());
  }

  @Test
  void testTooManyRequestsException(VertxTestContext testContext) {
    serviceMock.stubFor(get(urlPathEqualTo(PATH_TR)).willReturn(aResponse().withStatus(429)));

    new CS51Impl(provider)
        .fetchReport(REPORT_TR, BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(
                        () -> {
                          assertThat(ar.failed()).isTrue();
                          assertThat(ar.cause()).isInstanceOf(TooManyRequestsException.class);
                        })
                    .completeNow());
  }

  @Test
  void test403Response(VertxTestContext testContext) {
    serviceMock.stubFor(
        get(urlPathEqualTo(PATH_TR))
            .willReturn(aResponse().withStatus(403).withBodyFile("Exception_2010.json")));

    new CS51Impl(provider)
        .fetchReport(REPORT_TR, BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(
                        () -> {
                          assertThat(ar.failed()).isTrue();
                          assertThat(ar.cause())
                              .isInstanceOf(Counter51ClientException.class)
                              .hasMessageContaining(
                                  "Requestor is Not Authorized to Access Usage for Institution");
                        })
                    .completeNow());
  }

  @Test
  void test404ResponseWithBody(VertxTestContext testContext) {
    String errorBody =
        "{\"Severity\":\"Error\",\"Code\":3030,\"Message\":\"No Usage Available for Requested"
            + " Dates\"}";
    serviceMock.stubFor(
        get(urlPathEqualTo(PATH_TR)).willReturn(aResponse().withStatus(404).withBody(errorBody)));

    new CS51Impl(provider)
        .fetchReport(REPORT_TR, BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(
                        () -> {
                          assertThat(ar.failed()).isTrue();
                          assertThat(ar.cause())
                              .isInstanceOfSatisfying(
                                  Counter51ClientException.class,
                                  ex -> {
                                    assertThat(ex).hasMessage("HTTP 404: Not Found - " + errorBody);
                                    assertThat(ex.getStatusCode()).isEqualTo(404);
                                    assertThat(ex.getResponseBody()).isEqualTo(errorBody);
                                  });
                        })
                    .completeNow());
  }

  @Test
  void test404ResponseWithEmptyBody(VertxTestContext testContext) {
    serviceMock.stubFor(
        get(urlPathEqualTo(PATH_TR)).willReturn(aResponse().withStatus(404).withBody("")));

    new CS51Impl(provider)
        .fetchReport(REPORT_TR, BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(
                        () -> {
                          assertThat(ar.failed()).isTrue();
                          assertThat(ar.cause())
                              .isInstanceOfSatisfying(
                                  Counter51ClientException.class,
                                  ex -> {
                                    assertThat(ex).hasMessage("HTTP 404: Not Found - [no body]");
                                    assertThat(ex.getStatusCode()).isEqualTo(404);
                                    assertThat(ex.getResponseBody()).isEqualTo("[no body]");
                                  });
                        })
                    .completeNow());
  }

  @Test
  void test500ResponseWithHtmlBody(VertxTestContext testContext) {
    String htmlBody = "<!DOCTYPE html><html><body>Internal Server Error</body></html>";
    serviceMock.stubFor(
        get(urlPathEqualTo(PATH_TR)).willReturn(aResponse().withStatus(500).withBody(htmlBody)));

    new CS51Impl(provider)
        .fetchReport(REPORT_TR, BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(
                        () -> {
                          assertThat(ar.failed()).isTrue();
                          assertThat(ar.cause())
                              .isInstanceOfSatisfying(
                                  Counter51ClientException.class,
                                  ex -> {
                                    assertThat(ex)
                                        .hasMessage("HTTP 500: Server Error - " + htmlBody);
                                    assertThat(ex.getStatusCode()).isEqualTo(500);
                                    assertThat(ex.getResponseBody()).isEqualTo(htmlBody);
                                  });
                        })
                    .completeNow());
  }

  @Test
  void testResponseBodyAbbreviation(VertxTestContext testContext) {
    String longBody = "A".repeat(2500);
    serviceMock.stubFor(
        get(urlPathEqualTo(PATH_TR)).willReturn(aResponse().withStatus(404).withBody(longBody)));

    new CS51Impl(provider)
        .fetchReport(REPORT_TR, BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(
                        () -> {
                          assertThat(ar.failed()).isTrue();
                          assertThat(ar.cause())
                              .isInstanceOfSatisfying(
                                  Counter51ClientException.class,
                                  ex -> {
                                    String expectedMessage =
                                        "HTTP 404: Not Found - " + "A".repeat(1997) + "...";
                                    assertThat(ex).hasMessage(expectedMessage);
                                    assertThat(ex.getStatusCode()).isEqualTo(404);
                                    assertThat(ex.getResponseBody()).isEqualTo(longBody);
                                    assertThat(ex.getResponseBody()).hasSize(2500);
                                  });
                        })
                    .completeNow());
  }

  @Test
  void testFetchReportWithAdditionalAttributes(VertxTestContext testContext) {
    serviceMock.stubFor(
        get(urlPathEqualTo(PATH_TR))
            .willReturn(
                aResponse().withStatus(200).withBodyFile("TR_with_additional_attributes.json")));

    new CS51Impl(provider)
        .fetchReport(REPORT_TR, BEGIN_DATE, END_DATE)
        .onComplete(
            ar ->
                testContext
                    .verify(
                        () -> {
                          assertThat(ar.succeeded()).isFalse();
                          assertThat(ar.cause())
                              .isInstanceOf(Counter51ClientException.class)
                              .hasMessageContaining("Unrecognized field");
                        })
                    .completeNow());
  }
}
