package org.olf.erm.usage.harvester.endpoints;

import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public interface ServiceEndpointProvider {

  String getServiceType();

  String getServiceName();

  default String getServiceDescription() {
    return "";
  }

  default Boolean isAggregator() {
    return false;
  }

  ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator);
}
