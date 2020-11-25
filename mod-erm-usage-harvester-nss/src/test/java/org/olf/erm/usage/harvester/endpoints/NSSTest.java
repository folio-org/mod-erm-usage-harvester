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
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
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
import org.folio.rest.jaxrs.model.CounterReport;
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

  // TODO: test fetching report range

  @Test
  public void fetchReportWithAggregatorValidReport(TestContext context) {
    final NSS sep = new NSS(provider, aggregator);
    final String url = sep.buildURL(reportType, beginDate, endDate);

    LOG.info("Creating stub for: " + url);
    wireMockRule.stubFor(
        get(urlEqualTo(url.replaceAll(wireMockRule.url(""), "/")))
            .willReturn(aResponse().withBodyFile("nss-report-2016-03.xml")));

    Async async = context.async();
    sep.fetchReport(reportType, beginDate, endDate)
        .onFailure(context::fail)
        .onSuccess(
            list -> {
              assertThat(list).hasSize(1);
              CounterReport counterReport = list.get(0);
              assertThat(counterReport.getReport()).isNotNull();
              Report origReport =
                  JAXB.unmarshal(
                          Resources.getResource("__files/nss-report-2016-03.xml"),
                          CounterReportResponse.class)
                      .getReport()
                      .getReport()
                      .get(0);
              Report respReport = Counter4Utils.fromJSON(Json.encode(counterReport.getReport()));
              assertThat(respReport).usingRecursiveComparison().isEqualTo(origReport);
              async.complete();
            });
  }

  @Test
  public void fetchReportWithNullURL(TestContext context) {
    final NSS sep = new NSS(provider, null);

    Async async = context.async();
    sep.fetchReport(reportType, beginDate, endDate)
        .onComplete(
            ar -> {
              assertThat(ar.failed()).isTrue();
              assertThat(ar.cause().getMessage()).startsWith("Could not create request URL");
              async.complete();
            });
  }

  @Test
  public void fetchMultipleReports(TestContext context) {
    final NSS sep = new NSS(provider, aggregator);
    final String url1 =
        sep.buildURL("JR1", "2018-01-01", "2018-31-01").replaceFirst(wireMockRule.url(""), "/");
    final String url2 =
        sep.buildURL("JR1", "2018-02-01", "2018-02-28").replaceFirst(wireMockRule.url(""), "/");

    wireMockRule.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));

    Async async = context.async();

    Future<List<CounterReport>> f1 = sep.fetchReport("JR1", "2018-01-01", "2018-31-01");
    Future<List<CounterReport>> f2 = sep.fetchReport("JR1", "2018-02-01", "2018-02-28");
    CompositeFuture.join(f1, f2)
        .onComplete(
            v -> {
              context.verify(
                  v2 -> {
                    wireMockRule.verify(1, getRequestedFor(urlEqualTo(url1)));
                    wireMockRule.verify(1, getRequestedFor(urlEqualTo(url2)));
                  });
              async.complete();
            });
  }

  @Test
  public void fetchReportWithAggregatorInvalidReport(TestContext context) {
    final NSS sep = new NSS(provider, aggregator);
    final String url = sep.buildURL(reportType, beginDate, endDate);

    LOG.info("Creating stub for: " + url);
    wireMockRule.stubFor(
        get(urlEqualTo(url.replaceAll(wireMockRule.url(""), "/")))
            .willReturn(aResponse().withBodyFile("nss-report-2018-03-fail.xml")));

    Async async = context.async();
    sep.fetchReport(reportType, beginDate, endDate)
        .onSuccess(v -> context.fail())
        .onFailure(
            t -> {
              assertThat(t).isInstanceOf(InvalidReportException.class);
              assertThat(t.getMessage()).contains("1030", "RequestorID", "Insufficient");
              assertThat(t.getMessage()).doesNotContain("HelpUrl");
              async.complete();
            });
  }

  @Test
  public void fetchReportWithAggregatorInvalidResponse(TestContext context) {
    final NSS sep = new NSS(provider, aggregator);
    final String url = sep.buildURL(reportType, beginDate, endDate);

    LOG.info("Creating stub for: " + url);
    wireMockRule.stubFor(
        get(urlEqualTo(url.replaceAll(wireMockRule.url(""), "/")))
            .willReturn(aResponse().withStatus(404)));

    Async async = context.async();
    sep.fetchReport(reportType, beginDate, endDate)
        .onSuccess(v -> context.fail())
        .onFailure(
            t -> {
              context.verify(
                  v -> {
                    assertThat(t).isNotInstanceOf(InvalidReportException.class);
                    assertThat(t.getMessage().contains("404"));
                  });
              async.complete();
            });
  }

  @Test
  public void fetchReportWithAggregatorNoService(TestContext context) {
    final NSS sep = new NSS(provider, aggregator);
    wireMockRule.stop();

    Async async = context.async();
    sep.fetchReport(reportType, beginDate, endDate)
        .onFailure(t -> async.complete())
        .onSuccess(v -> context.fail());
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
    sep.fetchReport(reportType, beginDate, endDate)
        .onComplete(
            ar -> {
              context.verify(
                  v ->
                      wireMockRule.verify(
                          getRequestedFor(anyUrl())
                              .withQueryParam("Platform", equalTo("ACM Digital"))));
              async.complete();
            });
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
    sep.fetchReport(reportType, beginDate, endDate)
        .onComplete(
            v -> {
              context.verify(
                  v2 -> {
                    wireMockRule.verify(0, getRequestedFor(anyUrl()));
                    wireMockProxyRule.verify(1, getRequestedFor(anyUrl()));
                  });
              async.complete();
            });
  }
}
