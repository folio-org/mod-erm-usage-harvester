package org.olf.erm.usage.harvester.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import io.vertx.core.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.folio.rest.jaxrs.model.Aggregator;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestVia;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceEndpointFactoryTest {

  private UsageDataProvider usageDataProvider;

  @BeforeEach
  void setUp() throws IOException {
    usageDataProvider =
        Json.decodeValue(
            Resources.toString(
                Resources.getResource("__files/usage-data-provider.json"), StandardCharsets.UTF_8),
            UsageDataProvider.class);
  }

  @Test
  void testCreateServiceEndpoint() {
    assertThat(ServiceEndpointFactory.createServiceEndpoint(usageDataProvider)).isNotNull();
  }

  @Test
  void testCreateServiceEndpointNoImplementation() {
    usageDataProvider.getHarvestingConfig().getSushiConfig().setServiceType("test3");
    assertThatThrownBy(() -> ServiceEndpointFactory.createServiceEndpoint(usageDataProvider))
        .hasMessageContaining("No service implementation");
  }

  @Test
  void testCreateServiceEndpointWithoutHarvestVia() {
    usageDataProvider.getHarvestingConfig().setHarvestVia(null);
    assertThat(ServiceEndpointFactory.createServiceEndpoint(usageDataProvider)).isNotNull();
  }

  @Test
  void testCreateServiceEndpointIgnoresAggregatorConfig() {
    usageDataProvider
        .getHarvestingConfig()
        .withHarvestVia(HarvestVia.AGGREGATOR)
        .withAggregator(
            new Aggregator()
                .withId("4c66b956-23a8-4418-aef6-1c35dcdaccc4")
                .withVendorCode("ACMDL"));
    assertThat(ServiceEndpointFactory.createServiceEndpoint(usageDataProvider)).isNotNull();
  }
}
