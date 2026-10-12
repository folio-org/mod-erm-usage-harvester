package org.olf.erm.usage.harvester.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import io.vertx.core.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
}
