package org.olf.erm.usage.harvester.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("resource")
class ServiceEndpointFactoryTest {

  private static Vertx vertx;
  private UsageDataProvider usageDataProvider;

  @BeforeAll
  static void beforeAll() {
    vertx = Vertx.vertx();
  }

  @AfterAll
  static void afterAll() {
    vertx.close().await();
  }

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
    assertThat(ServiceEndpointFactory.createServiceEndpoint(vertx, usageDataProvider)).isNotNull();
  }

  @Test
  void testCreateServiceEndpointNoImplementation() {
    usageDataProvider.getHarvestingConfig().getSushiConfig().setServiceType("test3");
    assertThatThrownBy(() -> ServiceEndpointFactory.createServiceEndpoint(vertx, usageDataProvider))
        .hasMessageContaining("No service implementation");
  }
}
