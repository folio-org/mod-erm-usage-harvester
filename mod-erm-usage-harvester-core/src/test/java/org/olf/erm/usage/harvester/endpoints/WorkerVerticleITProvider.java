package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import java.time.YearMonth;
import java.util.List;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.tools.utils.VertxUtils;
import org.olf.erm.usage.harvester.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerVerticleITProvider implements ServiceEndpointProvider {

  Vertx vertx = VertxUtils.getVertxFromContextOrNew();
  WebClient client = WebClient.create(vertx);

  @Override
  public String getServiceType() {
    return "wvitp";
  }

  @Override
  public String getServiceName() {
    return "WorkerVerticleITProvider";
  }

  @Override
  public String getServiceDescription() {
    return "Test Provider";
  }

  @Override
  public ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {

    return new ServiceEndpoint() {
      private final Logger log = LoggerFactory.getLogger(WorkerVerticleITProvider.class);

      @Override
      public Future<List<CounterReport>> fetchReport(
          String report, String beginDate, String endDate) {
        log.info("Fetching report {} {} {}", report, beginDate, endDate);

        return client
            .getAbs(provider.getHarvestingConfig().getSushiConfig().getServiceUrl().concat("/"))
            .addQueryParam("report", report)
            .addQueryParam("begin", beginDate)
            .addQueryParam("end", endDate)
            .send()
            .map(
                resp -> {
                  List<YearMonth> months = DateUtil.getYearMonths(beginDate, endDate);
                  return months.stream()
                      .map(
                          ym ->
                              new CounterReport()
                                  .withReportName(report)
                                  .withReport(
                                      new Report().withAdditionalProperty("month", ym.toString()))
                                  .withRelease("4")
                                  .withProviderId("providerId")
                                  .withYearMonth(ym.toString()))
                      .toList();
                });
      }
    };
  }
}
