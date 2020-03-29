package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.ProxyOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.xml.bind.JAXB;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.niso.schemas.counter.Report;
import org.niso.schemas.sushi.Exception;
import org.niso.schemas.sushi.counter.CounterReportResponse;
import org.olf.erm.usage.counter41.Counter4Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NSS implements ServiceEndpoint {

  private static final Logger LOG = LoggerFactory.getLogger(NSS.class);
  private final WebClient client;
  private Vertx vertx;
  private UsageDataProvider provider;
  private AggregatorSetting aggregator;

  public NSS(UsageDataProvider provider, AggregatorSetting aggregator) {
    if (Vertx.currentContext() == null) this.vertx = Vertx.vertx();
    else {
      this.vertx = Vertx.currentContext().owner();
    }
    this.provider = provider;
    this.aggregator = aggregator;

    WebClientOptions options = new WebClientOptions();
    try {
      getProxy(new URI(aggregator.getServiceUrl()))
          .ifPresent(
              p -> {
                InetSocketAddress addr = (InetSocketAddress) p.address();
                options.setProxyOptions(
                    new ProxyOptions().setHost(addr.getHostString()).setPort(addr.getPort()));
              });
    } catch (java.lang.Exception e) {
      LOG.error("Error getting proxy: {}", e.getMessage());
    }
    this.client = WebClient.create(vertx, options);
  }

  public String buildURL(String report, String begin, String end) {
    String url =
        "%s?APIKey=%s&RequestorID="
            + "%s&CustomerID=%s&Report=%s&Release=%s&BeginDate=%s&EndDate=%s&Platform=%s&Format=xml";

    if (aggregator != null && aggregator.getAggregatorConfig() != null) {
      Map<String, Object> props = aggregator.getAggregatorConfig().getAdditionalProperties();
      return String.format(
          url,
          aggregator.getServiceUrl(),
          props.get("apiKey"),
          props.get("requestorId"),
          props.get("customerId"),
          report,
          props.get("reportRelease"),
          begin,
          end,
          provider.getHarvestingConfig().getAggregator().getVendorCode());
    }
    return null;
  }

  @Override
  public boolean isValidReport(String report) {
    return false;
  }

  @Override
  public Future<String> fetchSingleReport(String report, String beginDate, String endDate) {
    final String url = buildURL(report, beginDate, endDate);

    Promise<String> promise = Promise.promise();
    client
        .requestAbs(HttpMethod.GET, url)
        .send(
            ar -> {
              if (ar.succeeded()) {
                client.close();
                if (ar.result().statusCode() == 200) {
                  String result = ar.result().bodyAsString();
                  CounterReportResponse reportResponse =
                      JAXB.unmarshal(new StringReader(result), CounterReportResponse.class);
                  List<Exception> exceptions = Counter4Utils.getExceptions(reportResponse);
                  if (exceptions.isEmpty()
                      && reportResponse.getReport() != null
                      && !reportResponse.getReport().getReport().isEmpty()) {
                    Report report2 = reportResponse.getReport().getReport().get(0);
                    promise.complete(Counter4Utils.toJSON(report2));
                  } else {
                    promise.fail("Report not valid: " + Counter4Utils.getErrorMessages(exceptions));
                  }
                } else {
                  promise.fail(
                      url + " - " + ar.result().statusCode() + " : " + ar.result().statusMessage());
                }
              } else {
                promise.fail(ar.cause());
              }
            });
    return promise.future();
  }
}
