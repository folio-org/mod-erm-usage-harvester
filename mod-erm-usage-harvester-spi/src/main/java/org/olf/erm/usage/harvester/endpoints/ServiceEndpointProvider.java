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
