package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import java.util.List;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerVerticleITProviderFailsWithGetMessageNull implements ServiceEndpointProvider {

  @Override
  public String getServiceType() {
    return "wvitpfail";
  }

  @Override
  public String getServiceName() {
    return "WorkerVerticleITProviderFailsWithGetMessageNull";
  }

  @Override
  public String getServiceDescription() {
    return "Test Provider fails with java.lang.Exception.getMessage() == null";
  }

  @Override
  public ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {

    return new ServiceEndpoint() {
      private final Logger log =
          LoggerFactory.getLogger(WorkerVerticleITProviderFailsWithGetMessageNull.class);

      @Override
      public Future<List<CounterReport>> fetchReport(
          String report, String beginDate, String endDate) {
        log.info("Fetching report {} {} {}", report, beginDate, endDate);

        return Future.failedFuture(new Exception());
      }
    };
  }
}
