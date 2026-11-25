package org.olf.erm.usage.harvester.client;

import io.vertx.core.Vertx;
import java.util.Optional;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;

public final class ServiceEndpointFactory {

  private ServiceEndpointFactory() {}

  public static ServiceEndpoint createServiceEndpoint(
      Vertx vertx, UsageDataProvider usageDataProvider) {
    return Optional.ofNullable(ServiceEndpoint.create(vertx, usageDataProvider))
        .orElseThrow(() -> new IllegalStateException("No service implementation available"));
  }
}
