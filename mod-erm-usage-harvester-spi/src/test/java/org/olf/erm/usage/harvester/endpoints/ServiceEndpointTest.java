package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.Test;

public class ServiceEndpointTest {

  private static UsageDataProvider provider =
      new UsageDataProvider()
          .withHarvestingConfig(
              new HarvestingConfig()
                  .withSushiConfig(new SushiConfig().withServiceType("TestProviderType")));
  private static AggregatorSetting aggregator = new AggregatorSetting();

  @Test
  public void testGetAvailableProviders() {
    List<ServiceEndpointProvider> list = ServiceEndpoint.getAvailableProviders();
    assertThat(list.size()).isEqualTo(1);
    assertThat(list.get(0).getServiceName()).isEqualTo("TestProviderName");
    assertThat(list.get(0).getServiceType()).isEqualTo("TestProviderType");
  }

  @Test
  public void testCreateNoImplGiven() {
    ServiceEndpoint sep = ServiceEndpoint.create(provider, aggregator.withServiceType(""));
    assertThat(sep).isNull();
  }

  @Test
  public void testCreateNoImplFound() {
    ServiceEndpoint sep =
        ServiceEndpoint.create(
            provider, new AggregatorSetting().withServiceType("TestProviderType2"));
    assertThat(sep).isNull();
  }

  @Test
  public void testCreateOk() {
    ServiceEndpoint sep =
        ServiceEndpoint.create(
            provider, new AggregatorSetting().withServiceType("TestProviderType"));
    assertThat(sep).isInstanceOf(TestProviderImpl.class);
  }

  @Test
  public void testCreateOkNoAggregator() {
    ServiceEndpoint sep = ServiceEndpoint.create(provider, null);
    assertThat(sep).isInstanceOf(TestProviderImpl.class);
  }
}
