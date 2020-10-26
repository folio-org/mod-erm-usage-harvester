package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.folio.rest.tools.utils.VertxUtils;

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
      @Override
      public boolean isValidReport(String report) {
        return false;
      }

      @Override
      public Future<String> fetchSingleReport(String report, String beginDate, String endDate) {
        Promise<HttpResponse<Buffer>> promise = Promise.promise();
        client
            .getAbs(provider.getHarvestingConfig().getSushiConfig().getServiceUrl().concat("/"))
            .addQueryParam("report", report)
            .addQueryParam("begin", beginDate)
            .addQueryParam("end", endDate)
            .send(promise);

        return promise.future().map(HttpResponse::bodyAsString);
      }
    };
  }
}
