package org.olf.erm.usage.harvester.endpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.openapitools.client.model.SUSHIReportHeader;
import retrofit2.HttpException;

@RunWith(VertxUnitRunner.class)
public class CS50ImplTest {

  private static final String BEGIN_DATE = "2019-01-01";
  private static final String END_DATE = "2019-01-31";
  private static final String REPORT = "TR_J1";
  private static final String REPORT_PATH = "/sushi/reports/tr_j1";
  private static final String CUSTOMER_ID = "CustomerId123";
  private static final String REQUESTOR_ID = "RequestorId123";
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
    provider
        .getHarvestingConfig()
        .getSushiConfig()
        .setServiceUrl("http://localhost:" + wmRule.port() + "/sushi");
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
            .withQueryParam("requestor_id", equalTo(REQUESTOR_ID))
            .withQueryParam("begin_date", equalTo(BEGIN_DATE))
            .withQueryParam("end_date", equalTo(END_DATE)));
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
    String reportStr =
        Resources.toString(
            Resources.getResource("SampleReportMissingHeader.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH))
            .willReturn(aResponse().withStatus(200).withBody(reportStr)));

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
  public void testFetchReportMissingReportItems(TestContext context) throws IOException {
    String reportStr =
        Resources.toString(
            Resources.getResource("SampleReportMissingItems.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH))
            .willReturn(aResponse().withStatus(200).withBody(reportStr)));

    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t)
                      .isInstanceOf(InvalidReportException.class)
                      .hasMessageContaining("missing Report_Items");
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportEmptyReportItems(TestContext context) throws IOException {
    String reportStr =
        Resources.toString(
            Resources.getResource("SampleReportEmptyItems.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH))
            .willReturn(aResponse().withStatus(200).withBody(reportStr)));

    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t)
                      .isInstanceOf(InvalidReportException.class)
                      .hasMessageContaining("missing Report_Items");
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportWithException(TestContext context) throws IOException {
    String reportStr =
        Resources.toString(
            Resources.getResource("SampleReportExceptionError.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH))
            .willReturn(aResponse().withStatus(200).withBody(reportStr)));

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
    String expectedReportStr =
        Resources.toString(Resources.getResource("SampleReport.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH))
            .willReturn(aResponse().withStatus(200).withBody(expectedReportStr)));

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
    String expectedReportStr =
        Resources.toString(Resources.getResource("SampleReport.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH))
            .willReturn(aResponse().withStatus(202).withBody(expectedReportStr)));

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
    String expectedReportStr =
        Resources.toString(Resources.getResource("SampleReport.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH))
            .willReturn(aResponse().withStatus(400).withBody(expectedReportStr)));

    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).hasMessage(expectedReportStr);
                  verifyApiCall();
                }));
  }

  @Test
  public void testFetchReportErrorWithStatus400(TestContext context) throws IOException {
    String errStr = Resources.toString(Resources.getResource("error.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(400).withBody(errStr)));

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
    String errStr =
        Resources.toString(Resources.getResource("errorarray.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(200).withBody(errStr)));

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
    String errStr =
        Resources.toString(Resources.getResource("errorarray.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(202).withBody(errStr)));

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
    String errStr =
        Resources.toString(Resources.getResource("errorarray.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(400).withBody(errStr)));

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
    String errStr =
        Resources.toString(
            Resources.getResource("erroravailablereports.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(200).withBody(errStr)));

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
    wmRule.stubFor(get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(404)));

    new CS50Impl(provider)
        .fetchReport(REPORT, BEGIN_DATE, END_DATE)
        .onComplete(
            context.asyncAssertFailure(
                t -> {
                  assertThat(t).isInstanceOf(HttpException.class).hasMessageContaining("Not Found");
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
    String errStr = Resources.toString(Resources.getResource("error.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(202).withBody(errStr)));

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
    String errStr = Resources.toString(Resources.getResource("error.json"), StandardCharsets.UTF_8);

    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(200).withBody(errStr)));

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
    String reportStr =
        Resources.toString(Resources.getResource("SampleReportNull.json"), StandardCharsets.UTF_8);

    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH))
            .willReturn(aResponse().withStatus(200).withBody(reportStr)));

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
}
