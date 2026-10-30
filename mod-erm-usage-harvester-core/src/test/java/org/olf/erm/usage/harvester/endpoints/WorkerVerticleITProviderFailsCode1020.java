package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.tools.utils.VertxUtils;

public class WorkerVerticleITProviderFailsCode1020 implements ServiceEndpointProvider {

  Vertx vertx = VertxUtils.getVertxFromContextOrNew();
  WebClient client = WebClient.create(vertx);

  @Override
  public String getServiceType() {
    return "wvitpftmr";
  }

  @Override
  public String getServiceName() {
    return "WorkerVerticleITFailsCode1020";
  }

  @Override
  public String getServiceDescription() {
    return "Test Provider fails with Code1020Exception";
  }

  @Override
  public ServiceEndpoint create(UsageDataProvider provider) {
    return (report, beginDate, endDate) ->
        client
            .getAbs(provider.getHarvestingConfig().getSushiConfig().getServiceUrl().concat("/"))
            .addQueryParam("report", report)
            .addQueryParam("begin", beginDate)
            .addQueryParam("end", endDate)
            .send()
            .transform(ar -> Future.failedFuture(new Code1020Exception()));
  }
}
