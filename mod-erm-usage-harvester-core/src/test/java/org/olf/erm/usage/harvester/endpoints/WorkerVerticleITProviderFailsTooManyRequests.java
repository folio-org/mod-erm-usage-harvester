package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.folio.rest.jaxrs.model.AggregatorSetting;
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
  public ServiceEndpoint create(
      UsageDataProvider provider, AggregatorSetting aggregator, Vertx vertx) {
    WebClient client = WebClients.external(vertx);
    return (report, beginDate, endDate) ->
        client
            .getAbs(provider.getHarvestingConfig().getSushiConfig().getServiceUrl().concat("/"))
            .addQueryParam("report", report)
            .addQueryParam("begin", beginDate)
            .addQueryParam("end", endDate)
            .send()
            .transform(ar -> Future.failedFuture(new TooManyRequestsException()));
  }
}
