package org.olf.erm.usage.harvester.endpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import java.io.File;
import java.io.IOException;
import org.apache.log4j.Logger;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.io.Resources;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class NSSTest {


  @Rule
  public Timeout timeoutRule = Timeout.seconds(5);
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
  @Rule
  public RunTestOnContext ctx = new RunTestOnContext();

  private static final Logger LOG = Logger.getLogger(NSSTest.class);
  private UsageDataProvider provider;
  private AggregatorSetting aggregator;

  private static final String reportType = "JR1";
  private static final String endDate = "2016-03-31";
  private static final String beginDate = "2016-03-01";

  @Before
  public void setup(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {
    provider = new ObjectMapper().readValue(
        new File(Resources.getResource("__files/usage-data-provider.json").getFile()),
        UsageDataProvider.class);
    aggregator =
        new ObjectMapper()
            .readValue(new File(Resources.getResource("__files/aggregator-setting.json").getFile()),
                AggregatorSetting.class)
            .withServiceUrl(wireMockRule.url("mockedAPI"));
    LOG.info("Setting Aggregator URL to: " + aggregator.getServiceUrl());
  }

  @Test
  public void fetchSingleReportWithAggregatorValidReport(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {

    final ServiceEndpoint sep = ServiceEndpoint.create(provider, aggregator);
    final String url = ((NSS) sep).buildURL(reportType, beginDate, endDate);

    LOG.info("Creating stub for: " + url);
    stubFor(get(urlEqualTo(url.replaceAll(wireMockRule.url(""), "/")))
        .willReturn(aResponse().withBodyFile("nss-report-2016-03.xml")));

    Async async = context.async();
    Future<String> fetchSingleReport = sep.fetchSingleReport(reportType, beginDate, endDate);
    fetchSingleReport.setHandler(ar -> {
      if (ar.succeeded()) {
        context.assertTrue(ar.succeeded());
        context.assertTrue(ar.result().startsWith("<!-- wiremock -->"));
        async.complete();
      } else {
        System.out.println(ar.cause());
        context.fail();
      }
    });
  }

  @Test
  public void fetchSingleReportWithAggregatorInvalidReport(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {
    final ServiceEndpoint sep = ServiceEndpoint.create(provider, aggregator);
    final String url = ((NSS) sep).buildURL(reportType, beginDate, endDate);

    LOG.info("Creating stub for: " + url);
    stubFor(get(urlEqualTo(url.replaceAll(wireMockRule.url(""), "/")))
        .willReturn(aResponse().withBodyFile("nss-report-2018-03-fail.xml")));

    Async async = context.async();
    Future<String> fetchSingleReport = sep.fetchSingleReport(reportType, beginDate, endDate);
    fetchSingleReport.setHandler(ar -> {
      if (ar.failed()) {
        async.complete();
      } else {
        context.fail();
      }
    });
  }

  @Test
  public void fetchSingleReportWithAggregatorInvalidResponse(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {
    final ServiceEndpoint sep = ServiceEndpoint.create(provider, aggregator);
    final String url = ((NSS) sep).buildURL(reportType, beginDate, endDate);

    LOG.info("Creating stub for: " + url);
    stubFor(get(urlEqualTo(url.replaceAll(wireMockRule.url(""), "/")))
        .willReturn(aResponse().withStatus(404)));

    Async async = context.async();
    Future<String> fetchSingleReport = sep.fetchSingleReport(reportType, beginDate, endDate);
    fetchSingleReport.setHandler(ar -> {
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
  public void fetchSingleReportWithAggregatorNoService(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {
    final ServiceEndpoint sep = ServiceEndpoint.create(provider, aggregator);

    wireMockRule.stop();

    Async async = context.async();
    Future<String> fetchSingleReport = sep.fetchSingleReport(reportType, beginDate, endDate);
    fetchSingleReport.setHandler(ar -> {
      if (ar.succeeded()) {
        context.fail();
      } else {
        context.assertTrue(ar.failed());
        async.complete();
      }
    });
  }

}
