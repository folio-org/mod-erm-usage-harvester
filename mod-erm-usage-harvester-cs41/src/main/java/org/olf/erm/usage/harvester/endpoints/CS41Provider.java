package org.olf.erm.usage.harvester.endpoints;

import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public class CS41Provider implements ServiceEndpointProvider {

  @Override
  public String getServiceType() {
    return "cs41";
  }

  @Override
  public String getServiceName() {
    return "Counter-Sushi 4.1";
  }

  @Override
  public ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator) {
    return new CS41Impl(provider);
  }



}
