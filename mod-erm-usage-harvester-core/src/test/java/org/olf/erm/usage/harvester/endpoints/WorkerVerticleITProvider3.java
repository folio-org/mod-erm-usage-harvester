package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.tools.utils.VertxUtils;
import org.olf.erm.usage.harvester.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerVerticleITProvider3 implements ServiceEndpointProvider {

  Vertx vertx = VertxUtils.getVertxFromContextOrNew();
  WebClient client = WebClient.create(vertx);
  AtomicInteger counter = new AtomicInteger(0);

  @Override
  public String getServiceType() {
    return "wvitp3";
  }

  @Override
  public String getServiceName() {
    return "WorkerVerticleITProvider3";
  }

  @Override
  public String getServiceDescription() {
    return "Test Provider with too many requests error";
  }

  @Override
  public ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {

    return new ServiceEndpoint() {
      private final Logger log = LoggerFactory.getLogger(WorkerVerticleITProvider3.class);

      @Override
      public Future<List<CounterReport>> fetchReport(
          String report, String beginDate, String endDate) {
        log.info("Fetching report {} {} {}", report, beginDate, endDate);

        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          return Future.failedFuture(e);
        }

        return client
            .getAbs(provider.getHarvestingConfig().getSushiConfig().getServiceUrl().concat("/"))
            .addQueryParam("report", report)
            .addQueryParam("begin", beginDate)
            .addQueryParam("end", endDate)
            .send()
            .map(
                resp -> {
                  if (beginDate.equals("2020-01-01") && endDate.equals("2020-04-30")) {
                    if (counter.incrementAndGet() <= 2) {
                      throw new TooManyRequestsException();
                    }
                  }
                  if (beginDate.equals("2018-01-01") && endDate.equals("2018-12-31")) {
                    throw new InvalidReportException("Missing data for Month 2018-03");
                  }
                  if (beginDate.equals("2018-03-01") && endDate.equals("2018-03-31")) {
                    throw new InvalidReportException("No data for Month 2018-03");
                  }
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
                      .collect(Collectors.toList());
                });
      }
    };
  }
}
