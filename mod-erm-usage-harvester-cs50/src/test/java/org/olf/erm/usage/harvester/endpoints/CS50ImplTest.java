package org.olf.erm.usage.harvester.endpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.endpoints.CS50Impl.MAX_ERROR_BODY_LENGTH;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_ERROR_CODE;
import static org.olf.erm.usage.harvester.endpoints.TooManyRequestsException.TOO_MANY_REQUEST_STR;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openapitools.client.model.COUNTERTitleReport;
import org.openapitools.client.model.SUSHIErrorModel;
import org.openapitools.client.model.SUSHIReportHeader;
import retrofit2.HttpException;

@RunWith(VertxUnitRunner.class)
public class CS50ImplTest {

  private static final String BEGIN_DATE = "2019-01-01";
  private static final String END_DATE = "2019-01-31";
  private static final String REPORT = "TR_J1";
  private static final String REPORT_PATH = "/sushi/reports/tr_j1";
  private static final String CUSTOMER_ID = "CustomerId123";
  private static final String REQUESTOR_ID_QUERY = "requestor_id";
  private static final String REQUESTOR_ID = "RequestorId123";
  private static final String API_KEY_QUERY = "api_key";
  private static final String API_KEY = "ApiKey123";
  private static final COUNTERTitleReport emptyReport;
  private static UsageDataProvider provider;
  private static final Gson gson = new Gson();

  @Rule public WireMockRule wmRule = new WireMockRule(new WireMockConfiguration().dynamicPort());
  @Rule public WireMockRule proxyRule = new WireMockRule(new WireMockConfiguration().dynamicPort());
  @Rule public Timeout timeout = new Timeout(5, TimeUnit.SECONDS);

  static {
    emptyReport = new COUNTERTitleReport();
    emptyReport.setReportHeader(new SUSHIReportHeader());
  }

  @Before
  public void before() {
    provider = createTestProvider();
    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(URI uri) {
            return Collections.singletonList(Proxy.NO_PROXY);
          }

          @Override
          public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {}
        });
  }

  private UsageDataProvider createTestProvider() {
    return new UsageDataProvider()
        .withSushiCredentials(
            new SushiCredentials()
                .withCustomerId(CUSTOMER_ID)
                .withPlatform("")
                .withRequestorId(REQUESTOR_ID))
        .withHarvestingConfig(
            new HarvestingConfig()
                .withReportRelease(5)
                .withSushiConfig(
                    new SushiConfig()
                        .withServiceUrl("http://localhost:" + wmRule.port() + "/sushi")
                        .withServiceType("cs50")));
  }

  private void verifyApiCall() {
    wmRule.verify(
        getRequestedFor(urlPathEqualTo(REPORT_PATH))
            .withQueryParam("customer_id", equalTo(CUSTOMER_ID))
            .withQueryParam(REQUESTOR_ID_QUERY, equalTo(REQUESTOR_ID))
            .withQueryParam("begin_date", equalTo(BEGIN_DATE))
            .withQueryParam("end_date", equalTo(END_DATE)));
  }

  private String createStubWithResource(int code, String resourceName) throws IOException {
    String body = Resources.toString(Resources.getResource(resourceName), StandardCharsets.UTF_8);
    return createStubWithBody(code, body);
  }

  private String createStubWithBody(int code, String body) {
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(code).withBody(body)));
    return body;
  }

  @Test
  public void testProxy(TestContext context) {
    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(URI uri) {
            return Collections.singletonList(
                new Proxy(Type.HTTP, new InetSocketAddress("localhost", proxyRule.port())));
          }

          @Override
          public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}
        });

    proxyRule.stubFor(get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(404)));

    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                v -> {
                  proxyRule.verify(1, getRequestedFor(urlPathEqualTo(REPORT_PATH)));
                  wmRule.verify(0, getRequestedFor(urlPathEqualTo(REPORT_PATH)));
                }));
  }

  @Test
  public void testFetchReportNoHeader(TestContext context) throws IOException {
    createStubWithResource(200, "SampleReportMissingHeader.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t)
                      .isInstanceOf(InvalidReportException.class)
                      .hasMessageContaining("missing Report_Header");
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportTooManyRequestsByErrorCode(TestContext context) throws IOException {
    String reportStr =
        Resources.toString(
            Resources.getResource("SampleReportEmptyItems.json"), StandardCharsets.UTF_8);
    COUNTERTitleReport tr = gson.fromJson(reportStr, COUNTERTitleReport.class);
    SUSHIErrorModel error = new SUSHIErrorModel();
    error.setCode(TOO_MANY_REQUEST_ERROR_CODE);
    error.setMessage("");
    tr.getReportHeader().setExceptions(List.of(error));
    createStubWithBody(200, gson.toJson(tr));

    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).isInstanceOf(TooManyRequestsException.class);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportTooManyRequestsByErrorMessage(TestContext context) throws IOException {
    String reportStr =
        Resources.toString(
            Resources.getResource("SampleReportEmptyItems.json"), StandardCharsets.UTF_8);
    COUNTERTitleReport tr = gson.fromJson(reportStr, COUNTERTitleReport.class);
    SUSHIErrorModel error = new SUSHIErrorModel();
    error.setCode(1);
    error.setMessage(TOO_MANY_REQUEST_STR);
    tr.getReportHeader().setExceptions(List.of(error));
    createStubWithBody(200, gson.toJson(tr));

    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).isInstanceOf(TooManyRequestsException.class);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportTooManyRequestsByHttpStatusCode(TestContext context) {
    createStubWithBody(429, null);
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).isInstanceOf(TooManyRequestsException.class);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportNoReportItems(TestContext context) throws IOException {
    createStubWithResource(200, "SampleReportMissingItems.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertSuccess(
                list -> {
                  assertThat(list).hasSize(1);
                  assertThat(list.get(0).getReport().getAdditionalProperties().get("Report_Items"))
                      .isNotNull();
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportEmptyReportItems(TestContext context) throws IOException {
    createStubWithResource(200, "SampleReportEmptyItems.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertSuccess(
                list -> {
                  assertThat(list).hasSize(1);
                  assertThat(list.get(0).getReport().getAdditionalProperties().get("Report_Items"))
                      .isNotNull();
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportWithException(TestContext context) throws IOException {
    createStubWithResource(200, "SampleReportExceptionError.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  System.out.println(t.toString());
                  assertThat(t)
                      .isInstanceOf(InvalidReportException.class)
                      .hasMessageContaining("Invalid Customer Id");
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportOkWithStatus200(TestContext context) throws IOException {
    String expectedReportStr = createStubWithResource(200, "SampleReport.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertSuccess(
                list -> {
                  assertThat(list).hasSize(1);
                  Report receivedReport = list.get(0).getReport();
                  Report expectedReport =
                      Json.decodeValue(
                          gson.toJson(gson.fromJson(expectedReportStr, COUNTERTitleReport.class)),
                          Report.class);
                  assertThat(receivedReport)
                      .usingRecursiveComparison()
                      .ignoringCollectionOrder()
                      .isEqualTo(expectedReport);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportOkWithStatus202(TestContext context) throws IOException {
    String expectedReportStr = createStubWithResource(202, "SampleReport.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertSuccess(
                list -> {
                  assertThat(list).hasSize(1);
                  Report receivedReport = list.get(0).getReport();
                  Report expectedReport =
                      Json.decodeValue(
                          gson.toJson(gson.fromJson(expectedReportStr, COUNTERTitleReport.class)),
                          Report.class);
                  assertThat(receivedReport)
                      .usingRecursiveComparison()
                      .ignoringCollectionOrder()
                      .isEqualTo(expectedReport);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportOkWithStatus400(TestContext context) throws IOException {
    String expectedReportStr = createStubWithResource(400, "SampleReport.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t)
                      .hasMessage(StringUtils.abbreviate(expectedReportStr, MAX_ERROR_BODY_LENGTH));
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportWithInvalidMetricType(TestContext context) throws IOException {
    String expectedReportStr = createStubWithResource(200, "SampleReportInvalidMetricType.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t)
                      .hasMessage(StringUtils.abbreviate(expectedReportStr, MAX_ERROR_BODY_LENGTH));
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportErrorWithStatus400(TestContext context) throws IOException {
    String errStr = createStubWithResource(400, "error.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).hasMessage(errStr);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportErrorArrayWithStatus200(TestContext context) throws IOException {
    String errStr = createStubWithResource(200, "errorarray.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).hasMessage(errStr);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportErrorArrayWithStatus202(TestContext context) throws IOException {
    String errStr = createStubWithResource(202, "errorarray.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).hasMessage(errStr);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportErrorArrayWithStatus400(TestContext context) throws IOException {
    String errStr = createStubWithResource(400, "errorarray.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).hasMessage(errStr);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportErrorAvailableReportsArrayWithStatus200(TestContext context)
      throws IOException {
    String errStr = createStubWithResource(200, "erroravailablereports.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).hasMessage(errStr);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReport404(TestContext context) {
    createStubWithBody(404, null);
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t.getCause())
                      .isInstanceOf(HttpException.class)
                      .hasMessageContaining("Not Found");
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportNoService(TestContext context) {
    wmRule.stop();

    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(t -> assertThat(t).isInstanceOf(ConnectException.class)));
  }

  @Test
  public void testFetchReportGarbage(TestContext context) {
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH))
            .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)));

    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).isInstanceOf(IOException.class);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportNoSuchMethod(TestContext context) {
    new CS50Impl(provider)
        .fetchReport("XY_99", BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> assertThat(t).isInstanceOf(NoSuchMethodException.class)));
  }

  @Test
  public void testFetchReportErrorWithStatus202(TestContext context) throws IOException {
    String errStr = createStubWithResource(202, "error.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).hasMessage(errStr);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportErrorWithStatus200(TestContext context) throws IOException {
    String errStr = createStubWithResource(200, "error.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).hasMessage(errStr);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportNullWithStatus200(TestContext context) throws IOException {
    createStubWithResource(200, "SampleReportNull.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t)
                      .isInstanceOf(InvalidReportException.class)
                      .hasMessage("Report not valid: null");
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchMultipleMonthsWithEmptyMonths(TestContext context) throws IOException {
    createStubWithResource(200, "reports/dr_with_empty_months.json");
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(context.asyncAssertSuccess(list -> assertThat(list).hasSize(3)));
  }

  @Test
  public void testAuthKeyRequestorId(TestContext context) {
    createStubWithBody(404, null);
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                list ->
                    wmRule.verify(
                        getRequestedFor(urlPathEqualTo(REPORT_PATH))
                            .withQueryParam(REQUESTOR_ID_QUERY, equalTo(REQUESTOR_ID))
                            .withQueryParam(API_KEY_QUERY, absent()))));
  }

  @Test
  public void testAuthKeyApiKey(TestContext context) {
    createStubWithBody(404, null);
    provider.getSushiCredentials().setApiKey(API_KEY);
    provider.getSushiCredentials().setRequestorId(null);
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                list ->
                    wmRule.verify(
                        getRequestedFor(urlPathEqualTo(REPORT_PATH))
                            .withQueryParam(REQUESTOR_ID_QUERY, absent())
                            .withQueryParam(API_KEY_QUERY, equalTo(API_KEY)))));
  }

  @Test
  public void testAuthKeyBoth(TestContext context) {
    createStubWithBody(404, null);
    provider.getSushiCredentials().setApiKey(API_KEY);
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                list ->
                    wmRule.verify(
                        getRequestedFor(urlPathEqualTo(REPORT_PATH))
                            .withQueryParam(REQUESTOR_ID_QUERY, equalTo(REQUESTOR_ID))
                            .withQueryParam(API_KEY_QUERY, equalTo(API_KEY)))));
  }

  @Test
  public void testAuthKeyNone(TestContext context) {
    createStubWithBody(404, null);
    provider.getSushiCredentials().setRequestorId(null);
    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                list ->
                    wmRule.verify(
                        getRequestedFor(urlPathEqualTo(REPORT_PATH))
                            .withQueryParam(REQUESTOR_ID_QUERY, absent())
                            .withQueryParam(API_KEY_QUERY, absent()))));
  }
}
