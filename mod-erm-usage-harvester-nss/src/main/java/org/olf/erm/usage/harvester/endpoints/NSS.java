package org.olf.erm.usage.harvester.endpoints;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.bind.JAXB;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.niso.schemas.counter.Report;
import org.niso.schemas.sushi.Exception;
import org.niso.schemas.sushi.ExceptionSeverity;
import org.niso.schemas.sushi.counter.CounterReportResponse;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.WebClient;

public class NSS implements ServiceEndpoint {

  private Vertx vertx;
  private UsageDataProvider provider;
  private AggregatorSetting aggregator;

  public NSS(UsageDataProvider provider, AggregatorSetting aggregator) {
    if (Vertx.currentContext() == null)
      this.vertx = Vertx.vertx();
    else {
      this.vertx = Vertx.currentContext().owner();
    }
    this.provider = provider;
    this.aggregator = aggregator;
  }

  public String buildURL(String report, String begin, String end) {
    String url = "%s?APIKey=%s&RequestorID="
        + "%s&CustomerID=%s&Report=%s&Release=%s&BeginDate=%s&EndDate=%s&Platform=%s&Format=xml";

    if (aggregator != null && aggregator.getAggregatorConfig() != null) {
      Map<String, Object> props = aggregator.getAggregatorConfig().getAdditionalProperties();
      return String.format(url, aggregator.getServiceUrl(), props.get("apiKey"),
          props.get("requestorId"), props.get("customerId"), report, props.get("reportRelease"),
          begin, end, provider.getHarvestingConfig().getAggregator().getVendorCode());
    }
    return null;
  }

  @Override
  public boolean isValidReport(String report) {
    return false;
  }

  public boolean isValidReport(CounterReportResponse response) {
    List<Exception> exceptions = response.getException()
        .stream()
        .filter(e -> e.getSeverity().equals(ExceptionSeverity.ERROR)
            || e.getSeverity().equals(ExceptionSeverity.FATAL))
        .collect(Collectors.toList());
    return exceptions.isEmpty();
  }

  @Override
  public Future<String> fetchSingleReport(String report, String beginDate, String endDate) {
    final String url = buildURL(report, beginDate, endDate);

    Future<String> future = Future.future();
    WebClient client = WebClient.create(vertx);
    client.requestAbs(HttpMethod.GET, url).send(ar -> {
      if (ar.succeeded()) {
        client.close();
        if (ar.result().statusCode() == 200) {
          String result = ar.result().bodyAsString();
          CounterReportResponse reportResponse =
              JAXB.unmarshal(new StringReader(result), CounterReportResponse.class);
          if (isValidReport(reportResponse) && !reportResponse.getReport().getReport().isEmpty()) {
            Report report2 = reportResponse.getReport().getReport().get(0);
            future.complete(Tool.toJSON(report2));
          } else {
            future.fail("Report not valid");
          }
        } else {
          future.fail(url + " - " + ar.result().statusCode() + " : " + ar.result().statusMessage());
        }
      } else {
        future.fail(ar.cause());
      }
    });
    return future;
  }


}
