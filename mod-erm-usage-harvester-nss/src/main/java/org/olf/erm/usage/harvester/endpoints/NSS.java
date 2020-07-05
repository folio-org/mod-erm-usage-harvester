package org.olf.erm.usage.harvester.endpoints;

import io.netty.handler.codec.http.QueryStringEncoder;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.ProxyOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.xml.bind.JAXB;
import org.folio.rest.jaxrs.model.Aggregator;
import org.folio.rest.jaxrs.model.AggregatorConfig;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.HarvestingConfig;
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
  private final UsageDataProvider provider;
  private final AggregatorSetting aggregator;

  public NSS(UsageDataProvider provider, AggregatorSetting aggregator) {
    Vertx vertx;
    if (Vertx.currentContext() == null) vertx = Vertx.vertx();
    else {
      vertx = Vertx.currentContext().owner();
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
    Map<String, Object> props =
        Optional.ofNullable(aggregator)
            .map(AggregatorSetting::getAggregatorConfig)
            .map(AggregatorConfig::getAdditionalProperties)
            .orElse(null);
    Aggregator providerAggregator =
        Optional.ofNullable(provider)
            .map(UsageDataProvider::getHarvestingConfig)
            .map(HarvestingConfig::getAggregator)
            .orElse(null);

    if (props != null && providerAggregator != null) {
      QueryStringEncoder queryStringEncoder = new QueryStringEncoder(aggregator.getServiceUrl());
      queryStringEncoder.addParam("APIKey", Objects.toString(props.get("apiKey"), ""));
      queryStringEncoder.addParam("RequestorID", Objects.toString(props.get("requestorId"), ""));
      queryStringEncoder.addParam("CustomerID", Objects.toString(props.get("customerId"), ""));
      queryStringEncoder.addParam("Report", report);
      queryStringEncoder.addParam("Release", Objects.toString(props.get("reportRelease"), ""));
      queryStringEncoder.addParam("BeginDate", begin);
      queryStringEncoder.addParam("EndDate", end);
      queryStringEncoder.addParam(
          "Platform", provider.getHarvestingConfig().getAggregator().getVendorCode());
      queryStringEncoder.addParam("Format", "xml");
      return queryStringEncoder.toString();
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

    if (url == null) {
      return Future.failedFuture("Could not create request URL due to missing parameters.");
    }

    Promise<String> promise = Promise.promise();
    try {
      client
          .getAbs(url)
          .send(
              ar -> {
                if (ar.succeeded()) {
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
                      promise.fail(
                          "Report not valid: " + Counter4Utils.getErrorMessages(exceptions));
                    }
                  } else {
                    promise.fail(
                        url
                            + " - "
                            + ar.result().statusCode()
                            + " : "
                            + ar.result().statusMessage());
                  }
                } else {
                  promise.fail(ar.cause());
                }
              });
    } catch (java.lang.Exception e) {
      return Future.failedFuture(e);
    }
    return promise.future();
  }
}
