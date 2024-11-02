package org.olf.erm.usage.harvester.endpoints;

import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public class CS51Provider implements ServiceEndpointProvider {

  @Override
  public String getServiceType() {
    return "cs51";
  }

  @Override
  public String getServiceName() {
    return "Counter-Sushi 5.1";
  }

  @Override
  public String getServiceDescription() {
    return "Implementation for Counter/Sushi 5.1";
  }

  @Override
  public ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {
    return new CS51Impl(provider);
  }
}
