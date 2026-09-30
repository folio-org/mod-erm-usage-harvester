package org.olf.erm.usage.harvester.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpointProvider;

/** Verifies which {@link ServiceEndpointProvider} implementations are shipped with the bundle. */
class ServiceEndpointProviderTest {

  private static UsageDataProvider providerWithServiceType(String serviceType) {
    return new UsageDataProvider()
        .withId("a1b2c3d4-0000-4000-8000-000000000001")
        .withSushiCredentials(
            new SushiCredentials().withCustomerId("customerId").withRequestorId("requestorId"))
        .withHarvestingConfig(
            new HarvestingConfig()
                .withReportRelease("5")
                .withSushiConfig(
                    new SushiConfig()
                        .withServiceType(serviceType)
                        .withServiceUrl("http://localhost/sushi")));
  }

  @Test
  void testAvailableServiceTypes() {
    assertThat(ServiceEndpoint.getAvailableProviders())
        .extracting(ServiceEndpointProvider::getServiceType)
        .containsExactlyInAnyOrder("cs50", "cs51");
  }

  @ParameterizedTest
  @ValueSource(strings = {"cs50", "cs51"})
  void testCreateSupportedServiceType(String serviceType) {
    assertThat(ServiceEndpoint.create(providerWithServiceType(serviceType))).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"cs41", "unknown"})
  void testCreateUnsupportedServiceType(String serviceType) {
    assertThat(ServiceEndpoint.create(providerWithServiceType(serviceType))).isNull();
  }
}
