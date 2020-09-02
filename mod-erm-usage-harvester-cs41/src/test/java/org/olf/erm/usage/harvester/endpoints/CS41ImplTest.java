package org.olf.erm.usage.harvester.endpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingXPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class CS41ImplTest {

  private static final String SUSHI_SERVICE = "/sushiService";
  private static final String REPORT_TYPE = "JR1";
  private static final String BEGIN_DATE = "2018-01-01";
  private static final String END_DATE = "2018-01-31";
  @Rule public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule public WireMockRule wireMockProxyRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule public WireMockRule wireMockRedirectRule = new WireMockRule(wireMockConfig().dynamicPort());

  private UsageDataProvider provider =
      new UsageDataProvider()
          .withId("67339c41-a3a7-4d19-83e2-c808ab99c8fe")
          .withHarvestingConfig(
              new HarvestingConfig().withReportRelease(4).withSushiConfig(new SushiConfig()))
          .withSushiCredentials(
              new SushiCredentials().withRequestorId("reqId1").withCustomerId("custId1"));

  @Before
  public void setup() {
    this.provider
        .getHarvestingConfig()
        .getSushiConfig()
        .setServiceUrl("http://localhost:" + wireMockRule.port() + SUSHI_SERVICE);
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
  public void fetchSingleReport(TestContext ctx) {
    CS41Impl cs41 = new CS41Impl(provider);

    wireMockRule.stubFor(
        post(urlPathEqualTo(SUSHI_SERVICE))
            .willReturn(aResponse().withStatus(200).withBodyFile("response1.xml")));

    Async async = ctx.async();
    cs41.fetchSingleReport(REPORT_TYPE, BEGIN_DATE, END_DATE)
        .setHandler(
            ar -> {
              assertThat(ar.succeeded()).isTrue();
              wireMockRule.verify(
                  1,
                  postRequestedFor(urlPathEqualTo(SUSHI_SERVICE))
                      .withRequestBody(
                          matchingXPath(
                                  "//ns:Requestor[ns:ID='reqId1' and ns:Name='' and ns:Email='']")
                              .withXPathNamespace("ns", "http://www.niso.org/schemas/sushi")));
              async.complete();
            });

    async.await(5000);
  }

  @Test
  public void testFetchSingleReportNoConnection(TestContext ctx) {
    CS41Impl cs41 = new CS41Impl(provider);

    wireMockRule.stubFor(
        post(urlPathEqualTo(SUSHI_SERVICE))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    Async async = ctx.async();
    cs41.fetchSingleReport(REPORT_TYPE, BEGIN_DATE, END_DATE)
        .setHandler(
            ar ->
                ctx.verify(
                    v -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause().getMessage()).contains("Error getting report");
                      async.complete();
                    }));
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

    final ServiceEndpoint sep = new CS41Impl(provider);

    wireMockRule.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));
    wireMockProxyRule.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));

    Async async = context.async();
    sep.fetchSingleReport(REPORT_TYPE, BEGIN_DATE, END_DATE).setHandler(ar -> async.complete());

    async.await(2000);

    wireMockRule.verify(0, postRequestedFor(anyUrl()));
    wireMockProxyRule.verify(1, postRequestedFor(anyUrl()));
  }

  @Test
  public void testHttpErrorMessage(TestContext context) {
    CS41Impl cs41 = new CS41Impl(provider);

    wireMockRule.stubFor(
        post(urlPathEqualTo(SUSHI_SERVICE))
            .willReturn(
                aResponse().withStatus(500).withHeader("TestHeaderKey", "TestHeaderValue")));

    Async async = context.async();
    cs41.fetchSingleReport(REPORT_TYPE, BEGIN_DATE, END_DATE)
        .setHandler(
            ar ->
                context.verify(
                    v -> {
                      assertThat(ar.failed()).isTrue();
                      assertThat(ar.cause().getMessage())
                          .contains(
                              Arrays.asList(
                                  "Error getting report",
                                  "status code 500",
                                  "TestHeaderKey",
                                  "TestHeaderValue"));
                      async.complete();
                    }));
  }

  @Test
  public void testHttpRedirect(TestContext context) {
    CS41Impl cs41 = new CS41Impl(provider);

    System.out.println(wireMockRedirectRule.url(""));

    wireMockRule.stubFor(
        post(urlPathEqualTo(SUSHI_SERVICE))
            .willReturn(
                aResponse().withStatus(302).withHeader("Location", wireMockRedirectRule.url(""))));
    wireMockRedirectRule.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(200)));

    Async async = context.async();
    cs41.fetchSingleReport(REPORT_TYPE, BEGIN_DATE, END_DATE).setHandler(ar -> async.complete());

    async.await(2000);

    wireMockRule.verify(1, postRequestedFor(anyUrl()));
    wireMockRedirectRule.verify(1, postRequestedFor(anyUrl()));
  }
}
