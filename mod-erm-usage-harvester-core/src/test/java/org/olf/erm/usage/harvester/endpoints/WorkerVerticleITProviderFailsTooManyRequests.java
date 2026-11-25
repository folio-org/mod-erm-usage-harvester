package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import java.util.List;
import org.folio.rest.jaxrs.model.CounterReport;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public class WorkerVerticleITProviderFailsTooManyRequests implements ServiceEndpointProvider {

  @Override
  public String getServiceType() {
    return "wvitpftmr";
  }

  @Override
  public String getServiceName() {
    return "WorkerVerticleITFailsTooManyRequests";
  }

  @Override
  public String getServiceDescription() {
    return "Test Provider fails with TooManyRequestsException";
  }

  @Override
  public ServiceEndpoint create(Vertx vertx, UsageDataProvider provider) {
    final WebClient client = WebClient.create(vertx);
    return new ServiceEndpoint() {
      @Override
      public Future<List<CounterReport>> fetchReport(
          String report, String beginDate, String endDate) {
        return client
            .getAbs(provider.getHarvestingConfig().getSushiConfig().getServiceUrl().concat("/"))
            .addQueryParam("report", report)
            .addQueryParam("begin", beginDate)
            .addQueryParam("end", endDate)
            .send()
            .transform(ar -> Future.failedFuture(new TooManyRequestsException()));
      }

      @Override
      public void close() {
        client.close();
      }
    };
  }
}
