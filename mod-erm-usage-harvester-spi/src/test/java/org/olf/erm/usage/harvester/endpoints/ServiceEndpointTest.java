package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.Vertx;
import java.util.List;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.jupiter.api.Test;

class ServiceEndpointTest {

  private final Vertx vertx = Vertx.vertx();

  private static UsageDataProvider provider =
      new UsageDataProvider()
          .withHarvestingConfig(
              new HarvestingConfig()
                  .withSushiConfig(new SushiConfig().withServiceType("TestProviderType")));

  @Test
  void testGetAvailableProviders() {
    List<ServiceEndpointProvider> list = ServiceEndpoint.getAvailableProviders();
    assertThat(list).hasSize(1);
    assertThat(list.getFirst().getServiceName()).isEqualTo("TestProviderName");
    assertThat(list.getFirst().getServiceType()).isEqualTo("TestProviderType");
  }

  @Test
  void testCreateNoImplGiven() {
    ServiceEndpoint sep =
        ServiceEndpoint.create(provider, new AggregatorSetting().withServiceType(""), vertx);
    assertThat(sep).isNull();
  }

  @Test
  void testCreateNoImplFound() {
    ServiceEndpoint sep =
        ServiceEndpoint.create(
            provider, new AggregatorSetting().withServiceType("TestProviderType2"), vertx);
    assertThat(sep).isNull();
  }

  @Test
  void testCreateOk() {
    ServiceEndpoint sep =
        ServiceEndpoint.create(
            provider, new AggregatorSetting().withServiceType("TestProviderType"), vertx);
    assertThat(sep).isInstanceOf(TestProviderImpl.class);
  }

  @Test
  void testCreateOkNoAggregator() {
    ServiceEndpoint sep = ServiceEndpoint.create(provider, null, vertx);
    assertThat(sep).isInstanceOf(TestProviderImpl.class);
  }

  @Test
  void testCreateNoHarvesterConfig() {
    ServiceEndpoint sep = ServiceEndpoint.create(new UsageDataProvider(), null, vertx);
    assertThat(sep).isNull();
  }

  @Test
  void testCreateNoSushiConfig() {
    ServiceEndpoint sep =
        ServiceEndpoint.create(
            new UsageDataProvider().withHarvestingConfig(new HarvestingConfig()), null, vertx);
    assertThat(sep).isNull();
  }
}
