package org.olf.erm.usage.harvester.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.junit5.RunTestOnContext;
import io.vertx.junit5.VertxExtension;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpointProvider;

/** Verifies which {@link ServiceEndpointProvider} implementations are shipped with the bundle. */
@ExtendWith(VertxExtension.class)
@SuppressWarnings("resource")
class ServiceEndpointProviderTest {

  @RegisterExtension RunTestOnContext runTestOnContext = new RunTestOnContext();

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
    assertThat(
            ServiceEndpoint.create(runTestOnContext.vertx(), providerWithServiceType(serviceType)))
        .isNotNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"cs50", "cs51"})
  void testRepeatedCreateAndCloseDoesNotCreateVertxInstances(String serviceType) {
    long vertxInstancesBefore = countVertxInstances();

    for (int i = 0; i < 5; i++) {
      ServiceEndpoint endpoint =
          ServiceEndpoint.create(runTestOnContext.vertx(), providerWithServiceType(serviceType));
      assertThat(endpoint).isNotNull();
      endpoint.close();
    }

    assertThat(countVertxInstances()).isEqualTo(vertxInstancesBefore);
  }

  /** Each Vertx instance runs exactly one blocked-thread-checker thread. */
  private static long countVertxInstances() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(Thread::isAlive)
        .filter(t -> t.getName().startsWith("vertx-blocked-thread-checker"))
        .count();
  }

  @ParameterizedTest
  @ValueSource(strings = {"cs41", "unknown"})
  void testCreateUnsupportedServiceType(String serviceType) {
    assertThat(
            ServiceEndpoint.create(runTestOnContext.vertx(), providerWithServiceType(serviceType)))
        .isNull();
  }
}
