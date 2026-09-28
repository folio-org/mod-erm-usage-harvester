package org.olf.erm.usage.harvester.client;

import java.util.Optional;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;

public final class ServiceEndpointFactory {

  private ServiceEndpointFactory() {}

  public static ServiceEndpoint createServiceEndpoint(UsageDataProvider usageDataProvider) {
    return Optional.ofNullable(ServiceEndpoint.create(usageDataProvider))
        .orElseThrow(() -> new IllegalStateException("No service implementation available"));
  }
}
