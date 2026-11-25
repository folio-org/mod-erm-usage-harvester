package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Vertx;
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
   * @param vertx the Vert.x instance the endpoint runs on
   * @param provider the usage data provider to harvest
   * @return the service endpoint
   */
  ServiceEndpoint create(Vertx vertx, UsageDataProvider provider);

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
