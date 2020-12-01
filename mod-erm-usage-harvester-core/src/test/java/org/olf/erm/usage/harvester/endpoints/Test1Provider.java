package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import java.util.List;
import java.util.stream.Collectors;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.Report;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.DateUtil;

public class Test1Provider implements ServiceEndpointProvider {

  @Override
  public String getServiceType() {
    return "test1";
  }

  @Override
  public String getServiceName() {
    return "test1";
  }

  @Override
  public ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {
    return (report, beginDate, endDate) -> {
      List<CounterReport> resultList =
          DateUtil.getYearMonths(beginDate, endDate).stream()
              .map(
                  ym ->
                      new CounterReport()
                          .withProviderId(provider.getId())
                          .withReportName(report)
                          .withYearMonth(ym.toString())
                          .withReport(new Report()))
              .collect(Collectors.toList());
      return Future.succeededFuture(resultList);
    };
  }
}
