package org.olf.erm.usage.harvester.endpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import io.vertx.ext.unit.Async;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.folio.rest.jaxrs.model.HarvestingConfig;
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
import retrofit2.adapter.rxjava2.HttpException;

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
  private static Gson gson = new Gson();

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

    String cr = gson.toJson(emptyReport);
    proxyRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(200).withBody(cr)));

    Async async = context.async();
    new CS50Impl(provider)
        .fetchSingleReport(REPORT, BEGIN_DATE, END_DATE)
        .setHandler(
            ar -> {
              assertThat(ar.succeeded()).isTrue();
              assertThat(ar.result()).isEqualTo(cr);
              proxyRule.verify(1, getRequestedFor(urlPathEqualTo(REPORT_PATH)));
              wmRule.verify(0, getRequestedFor(urlPathEqualTo(REPORT_PATH)));
              async.complete();
            });
  }

  @Test
  public void testFetchSingleReportNoHeader(TestContext context) {
    String cr = gson.toJson(new COUNTERTitleReport());
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(200).withBody(cr)));

    Async async = context.async();
    new CS50Impl(provider)
        .fetchSingleReport(REPORT, BEGIN_DATE, END_DATE)
        .setHandler(
            ar -> {
              assertThat(ar.failed()).isTrue();
              verifyApiCall();
              async.complete();
            });
  }

  @Test
  public void testFetchSingleReportOk(TestContext context) {
    String cr = gson.toJson(emptyReport);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(200).withBody(cr)));

    Async async = context.async();
    new CS50Impl(provider)
        .fetchSingleReport(REPORT, BEGIN_DATE, END_DATE)
        .setHandler(
            ar -> {
              assertThat(ar.succeeded()).isTrue();
              assertThat(ar.result()).isEqualTo(cr);
              verifyApiCall();
              async.complete();
            });
  }

  @Test
  public void testFetchSingleReportError(TestContext context) throws IOException {
    String errStr = Resources.toString(Resources.getResource("error.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(400).withBody(errStr)));

    Async async = context.async();
    new CS50Impl(provider)
        .fetchSingleReport(REPORT, BEGIN_DATE, END_DATE)
        .setHandler(
            ar -> {
              assertThat(ar.failed()).isTrue();
              assertThat(ar.cause()).hasMessageContaining("api_key Invalid");
              verifyApiCall();
              async.complete();
            });
  }

  @Test
  public void testFetchSingleReportErrorArray(TestContext context) throws IOException {
    String errStr =
        Resources.toString(Resources.getResource("error2.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(400).withBody(errStr)));

    Async async = context.async();
    new CS50Impl(provider)
        .fetchSingleReport(REPORT, BEGIN_DATE, END_DATE)
        .setHandler(
            ar -> {
              assertThat(ar.failed()).isTrue();
              assertThat(ar.cause()).hasMessageContaining("api_key Invalid");
              verifyApiCall();
              async.complete();
            });
  }

  @Test
  public void testFetchSingleReport404(TestContext context) {
    wmRule.stubFor(get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(404)));

    Async async = context.async();
    new CS50Impl(provider)
        .fetchSingleReport(REPORT, BEGIN_DATE, END_DATE)
        .setHandler(
            ar -> {
              assertThat(ar.failed()).isTrue();
              assertThat(ar.cause())
                  .isInstanceOf(HttpException.class)
                  .hasMessageContaining("Not Found");
              verifyApiCall();
              async.complete();
            });
  }

  @Test
  public void testFetchSingleReportNoService(TestContext context) {
    wmRule.stop();

    Async async = context.async();
    new CS50Impl(provider)
        .fetchSingleReport(REPORT, BEGIN_DATE, END_DATE)
        .setHandler(
            ar -> {
              assertThat(ar.failed()).isTrue();
              assertThat(ar.cause()).isInstanceOf(ConnectException.class);
              async.complete();
            });
  }

  @Test
  public void testFetchSingleReportGarbage(TestContext context) {
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH))
            .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)));

    Async async = context.async();
    new CS50Impl(provider)
        .fetchSingleReport(REPORT, BEGIN_DATE, END_DATE)
        .setHandler(
            ar -> {
              assertThat(ar.failed()).isTrue();
              assertThat(ar.cause()).isInstanceOf(IOException.class);
              verifyApiCall();
              async.complete();
            });
  }

  @Test
  public void testNoSuchMethod() {
    new CS50Impl(provider)
        .fetchSingleReport("XY_99", BEGIN_DATE, END_DATE)
        .setHandler(
            ar -> {
              assertThat(ar.failed()).isTrue();
              assertThat(ar.cause()).isInstanceOf(NoSuchMethodException.class);
            });
  }

  @Test
  public void testFetchSingleReportError202(TestContext context) throws IOException {
    String errStr = Resources.toString(Resources.getResource("error.json"), StandardCharsets.UTF_8);
    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH)).willReturn(aResponse().withStatus(202).withBody(errStr)));

    new CS50Impl(provider)
        .fetchSingleReport(REPORT, BEGIN_DATE, END_DATE)
        .setHandler(
            context.asyncAssertFailure(
                t ->
                    context.verify(
                        v -> {
                          assertThat(t.getMessage()).contains("api_key Invalid");
                          verifyApiCall();
                        })));
  }

  @Test
  public void testFetchSingleReportError200WithError(TestContext context) throws IOException {
    String errStr = Resources.toString(Resources.getResource("error.json"), StandardCharsets.UTF_8);

    SUSHIReportHeader header = new SUSHIReportHeader();
    header.setExceptions(
        Arrays.asList(
            gson.fromJson(errStr, SUSHIErrorModel.class),
            gson.fromJson(errStr, SUSHIErrorModel.class)));
    COUNTERTitleReport report = new COUNTERTitleReport();
    report.setReportHeader(header);

    wmRule.stubFor(
        get(urlPathEqualTo(REPORT_PATH))
            .willReturn(aResponse().withStatus(200).withBody(gson.toJson(report))));

    new CS50Impl(provider)
        .fetchSingleReport(REPORT, BEGIN_DATE, END_DATE)
        .setHandler(
            context.asyncAssertFailure(
                t ->
                    context.verify(
                        v -> {
                          assertThat(t.getMessage()).contains("api_key Invalid");
                          verifyApiCall();
                        })));
  }
}
