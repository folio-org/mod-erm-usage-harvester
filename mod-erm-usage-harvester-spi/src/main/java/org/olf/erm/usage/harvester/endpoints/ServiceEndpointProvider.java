package org.olf.erm.usage.harvester.endpoints;

import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public interface ServiceEndpointProvider {

  String getServiceType();

  /**
   * Returns the human-readable display name for this service endpoint implementation.
   *
   * <p>Naming convention for COUNTER implementations: "Counter {version}" (e.g., "Counter 5.0",
   * "Counter 5.1").
   *
   * @return the display name shown in UI dropdowns and API responses
   */
  String getServiceName();

  default String getServiceDescription() {
    return null;
  }

  /**
   * Creates a service endpoint for the given provider.
   *
   * <p>Must be called on a Vert.x context (e.g. in {@code Verticle.start()}). Implementations
   * should use the Vert.x instance of the current context and must not create their own.
   *
   * @param provider the usage data provider to harvest
   * @return the service endpoint
   */
  ServiceEndpoint create(UsageDataProvider provider);

  default String getReportRelease() {
    return null;
  }

  default List<String> getSupportedReports() {
    return Collections.emptyList();
  }

  default boolean isDefault() {
    return false;
  }
}
