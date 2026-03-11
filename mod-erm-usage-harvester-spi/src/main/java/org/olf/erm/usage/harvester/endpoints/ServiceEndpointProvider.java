package org.olf.erm.usage.harvester.endpoints;

import java.util.Collections;
import java.util.List;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public interface ServiceEndpointProvider {

  String getServiceType();

  /**
   * Returns the human-readable display name for this service endpoint implementation.
   *
   * <p>Naming convention for COUNTER implementations:
   *
   * <ul>
   *   <li>COUNTER 4.x and earlier: Use "Counter-Sushi {version}" (e.g., "Counter-Sushi 4.1")
   *   <li>COUNTER 5.x and later: Use "Counter {version}" (e.g., "Counter 5.0", "Counter 5.1")
   * </ul>
   *
   * <p>This reflects the naming change introduced in COUNTER Release 5, where the SUSHI API was
   * renamed to the COUNTER API.
   *
   * @return the display name shown in UI dropdowns and API responses
   */
  String getServiceName();

  default String getServiceDescription() {
    return null;
  }

  default Boolean isAggregator() {
    return false;
  }

  ServiceEndpoint create(UsageDataProvider provider, AggregatorSetting aggregator);

  default List<String> getConfigurationParameters() {
    return Collections.emptyList();
  }

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
