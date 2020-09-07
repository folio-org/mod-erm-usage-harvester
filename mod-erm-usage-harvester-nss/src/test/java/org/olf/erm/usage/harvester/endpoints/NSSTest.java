package org.olf.erm.usage.harvester.endpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.io.Resources;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import javax.xml.bind.JAXB;
import org.folio.rest.jaxrs.model.Aggregator;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niso.schemas.counter.Report;
import org.niso.schemas.sushi.counter.CounterReportResponse;
import org.olf.erm.usage.counter41.Counter4Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(VertxUnitRunner.class)
public class NSSTest {

  @Rule public Timeout timeoutRule = Timeout.seconds(5);
  @Rule public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule public WireMockRule wireMockProxyRule = new WireMockRule(wireMockConfig().dynamicPort());

  private static final Logger LOG = LoggerFactory.getLogger(NSSTest.class);
  private UsageDataProvider provider;
  private AggregatorSetting aggregator;

  private static final String reportType = "JR1";
  private static final String endDate = "2016-03-31";
  private static final String beginDate = "2016-03-01";

  @Before
  public void setup() throws IOException {
    provider =
        new ObjectMapper()
            .readValue(
                new File(Resources.getResource("__files/usage-data-provider.json").getFile()),
                UsageDataProvider.class);
    aggregator =
        new ObjectMapper()
            .readValue(
                new File(Resources.getResource("__files/aggregator-setting.json").getFile()),
                AggregatorSetting.class)
            .withServiceUrl(wireMockRule.url("mockedAPI"));
    LOG.info("Setting Aggregator URL to: " + aggregator.getServiceUrl());

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

  @Test
  public void fetchSingleReportWithAggregatorValidReport(TestContext context) {
    final NSS sep = new NSS(provider, aggregator);
    final String url = sep.buildURL(reportType, beginDate, endDate);

    LOG.info("Creating stub for: " + url);
    wireMockRule.stubFor(
        get(urlEqualTo(url.replaceAll(wireMockRule.url(""), "/")))
            .willReturn(aResponse().withBodyFile("nss-report-2016-03.xml")));

    Async async = context.async();
    Future<String> fetchSingleReport = sep.fetchSingleReport(reportType, beginDate, endDate);
    fetchSingleReport.setHandler(
        ar -> {
          if (ar.succeeded()) {
            context.assertTrue(ar.succeeded());

            Report origReport =
                JAXB.unmarshal(
                        Resources.getResource("__files/nss-report-2016-03.xml"),
                        CounterReportResponse.class)
                    .getReport()
                    .getReport()
                    .get(0);
            Report respReport = Counter4Utils.fromJSON(ar.result());
            assertThat(origReport).isEqualToComparingFieldByFieldRecursively(respReport);

            async.complete();
          } else {
            context.fail(ar.cause());
          }
        });
  }

  @Test
  public void fetchSingleReportWithNullURL(TestContext context) {
    final NSS sep = new NSS(provider, null);

    Async async = context.async();
    Future<String> fetchSingleReport = sep.fetchSingleReport(reportType, beginDate, endDate);
    fetchSingleReport.onComplete(
        ar -> {
          assertThat(ar.cause().getMessage()).startsWith("Could not create request URL");
          async.complete();
        });
  }

  @Test
  public void fetchMultipleMonths(TestContext context) {
    final NSS sep = new NSS(provider, aggregator);
    final String url1 =
        sep.buildURL("JR1", "2018-01-01", "2018-31-01").replaceFirst(wireMockRule.url(""), "/");
    final String url2 =
        sep.buildURL("JR1", "2018-02-01", "2018-02-28").replaceFirst(wireMockRule.url(""), "/");

    wireMockRule.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));

    Async async = context.async(2);
    sep.fetchSingleReport("JR1", "2018-01-01", "2018-31-01").onComplete(ar -> async.countDown());
    sep.fetchSingleReport("JR1", "2018-02-01", "2018-02-28").onComplete(ar -> async.countDown());

    async.await(5000);
    wireMockRule.verify(1, getRequestedFor(urlEqualTo(url1)));
    wireMockRule.verify(1, getRequestedFor(urlEqualTo(url2)));
  }

  @Test
  public void fetchSingleReportWithAggregatorInvalidReport(TestContext context) {
    final NSS sep = new NSS(provider, aggregator);
    final String url = sep.buildURL(reportType, beginDate, endDate);

    LOG.info("Creating stub for: " + url);
    wireMockRule.stubFor(
        get(urlEqualTo(url.replaceAll(wireMockRule.url(""), "/")))
            .willReturn(aResponse().withBodyFile("nss-report-2018-03-fail.xml")));

    Async async = context.async();
    Future<String> fetchSingleReport = sep.fetchSingleReport(reportType, beginDate, endDate);
    fetchSingleReport.setHandler(
        ar -> {
          if (ar.failed()) {
            LOG.info(ar.cause().getMessage());
            assertThat(ar.cause().getMessage()).contains("1030", "RequestorID", "Insufficient");
            assertThat(ar.cause().getMessage()).doesNotContain("HelpUrl");
            async.complete();
          } else {
            context.fail();
          }
        });
  }

  @Test
  public void fetchSingleReportWithAggregatorInvalidResponse(TestContext context) {
    final NSS sep = new NSS(provider, aggregator);
    final String url = sep.buildURL(reportType, beginDate, endDate);

    LOG.info("Creating stub for: " + url);
    wireMockRule.stubFor(
        get(urlEqualTo(url.replaceAll(wireMockRule.url(""), "/")))
            .willReturn(aResponse().withStatus(404)));

    Async async = context.async();
    Future<String> fetchSingleReport = sep.fetchSingleReport(reportType, beginDate, endDate);
    fetchSingleReport.setHandler(
        ar -> {
          if (ar.succeeded()) {
            context.fail();
          } else {
            context.assertTrue(ar.failed());
            context.assertTrue(ar.cause().getMessage().contains("404"));
            async.complete();
          }
        });
  }

  @Test
  public void fetchSingleReportWithAggregatorNoService(TestContext context) {
    final NSS sep = new NSS(provider, aggregator);

    wireMockRule.stop();

    Async async = context.async();
    Future<String> fetchSingleReport = sep.fetchSingleReport(reportType, beginDate, endDate);
    fetchSingleReport.setHandler(
        ar -> {
          if (ar.succeeded()) {
            context.fail();
          } else {
            context.assertTrue(ar.failed());
            async.complete();
          }
        });
  }

  @Test
  public void testIsValidReport() {
    CounterReportResponse reportValid =
        JAXB.unmarshal(
            Resources.getResource("__files/nss-report-2016-03.xml"), CounterReportResponse.class);
    CounterReportResponse reportInvalid =
        JAXB.unmarshal(
            Resources.getResource("__files/nss-report-2018-03-fail.xml"),
            CounterReportResponse.class);
    assertThat(Counter4Utils.getExceptions(reportValid)).isEmpty();
    assertThat(Counter4Utils.getExceptions(reportInvalid)).isNotEmpty();
  }

  @Test
  public void testWhiteSpacesInQueryParameter(TestContext context) {
    Aggregator aggregator = provider.getHarvestingConfig().getAggregator();
    aggregator.setVendorCode("ACM Digital");
    final NSS sep = new NSS(provider, this.aggregator);

    wireMockRule.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));
    Async async = context.async();
    sep.fetchSingleReport(reportType, beginDate, endDate)
        .onComplete(
            ar -> {
              context.verify(
                  v ->
                      wireMockRule.verify(
                          getRequestedFor(anyUrl())
                              .withQueryParam("Platform", equalTo("ACM Digital"))));
              async.complete();
            });

    ;
  }

  @Test
  public void testBuildURL() {
    NSS sep = new NSS(provider, this.aggregator);
    String url = sep.buildURL(reportType, beginDate, endDate);
    QueryStringDecoder decoder = new QueryStringDecoder(url);
    assertThat(decoder.parameters().get("APIKey").get(0)).isEqualTo("abc");

    aggregator.getAggregatorConfig().setAdditionalProperty("apiKey", null);
    sep = new NSS(provider, this.aggregator);
    url = sep.buildURL(reportType, beginDate, endDate);
    decoder = new QueryStringDecoder(url);
    assertThat(decoder.parameters().get("APIKey").get(0)).isEmpty();

    sep = new NSS(null, this.aggregator);
    assertThat(sep.buildURL(reportType, beginDate, endDate)).isNull();

    sep = new NSS(provider, null);
    assertThat(sep.buildURL(reportType, beginDate, endDate)).isNull();
  }

  @Test
  public void testProxy(TestContext context) {
    ProxySelector.setDefault(
        new ProxySelector() {
          @Override
          public List<Proxy> select(URI uri) {
            return Collections.singletonList(
                new Proxy(Type.HTTP, new InetSocketAddress("localhost", wireMockProxyRule.port())));
          }

          @Override
          public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}
        });

    final NSS sep = new NSS(provider, aggregator);

    wireMockRule.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));
    wireMockProxyRule.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));

    Async async = context.async();
    sep.fetchSingleReport(reportType, beginDate, endDate).setHandler(ar -> async.complete());

    async.await(2000);

    wireMockRule.verify(0, getRequestedFor(anyUrl()));
    wireMockProxyRule.verify(1, getRequestedFor(anyUrl()));
  }
}
