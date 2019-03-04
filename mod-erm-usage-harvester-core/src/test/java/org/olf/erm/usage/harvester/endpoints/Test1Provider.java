package org.olf.erm.usage.harvester.endpoints;

import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import io.vertx.core.Future;

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
    return new ServiceEndpoint() {

      @Override
      public boolean isValidReport(String report) {
        return false;
      }

      @Override
      public Future<String> fetchSingleReport(String report, String beginDate, String endDate) {
        return null;
      }
    };
  }
}
