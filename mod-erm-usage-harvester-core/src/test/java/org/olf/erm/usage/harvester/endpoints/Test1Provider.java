package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.stream.Collectors;
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
  public String getServiceDescription() {
    return "Test1 description";
  }

  @Override
  public String getReportRelease() {
    return "5";
  }

  @Override
  public List<String> getSupportedReports() {
    return List.of("TR", "DR");
  }

  @Override
  public boolean isDefault() {
    return true;
  }

  @Override
  public ServiceEndpoint create(Vertx vertx, UsageDataProvider provider) {
    return new ServiceEndpoint() {
      @Override
      public Future<List<CounterReport>> fetchReport(
          String report, String beginDate, String endDate) {
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
      }

      @Override
      public void close() {
        // nothing to release
      }
    };
  }
}
