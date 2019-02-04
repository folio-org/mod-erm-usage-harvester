package org.olf.erm.usage.harvester.endpoints;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import javax.xml.bind.JAXB;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.niso.schemas.counter.Report;
import org.niso.schemas.sushi.Exception;
import org.niso.schemas.sushi.counter.CounterReportResponse;
import org.olf.erm.usage.counter41.Counter4Utils;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.WebClient;

public class NSS implements ServiceEndpoint {

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

    Future<String> future = Future.future();
    WebClient client = WebClient.create(vertx);
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
                    future.complete(Counter4Utils.toJSON(report2));
                  } else {
                    future.fail("Report not valid: " + Counter4Utils.getErrorMessages(exceptions));
                  }
                } else {
                  future.fail(
                      url + " - " + ar.result().statusCode() + " : " + ar.result().statusMessage());
                }
              } else {
                future.fail(ar.cause());
              }
            });
    return future;
  }
}
