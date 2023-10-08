package org.olf.erm.usage.harvester.client;

import static io.vertx.core.Future.succeededFuture;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestVia;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ServiceEndpointFactoryTest {

  private UsageDataProvider usageDataProvider;

  @Before
  public void setUp() throws IOException {
    usageDataProvider =
        Json.decodeValue(
            Resources.toString(
                Resources.getResource("__files/usage-data-provider.json"), StandardCharsets.UTF_8),
            UsageDataProvider.class);
  }

  @Test
  public void testCreateServiceEndpoint(TestContext context) {
    new ServiceEndpointFactory(provider -> null)
        .createServiceEndpoint(usageDataProvider)
        .onComplete(context.asyncAssertSuccess(sep -> assertThat(sep).isNotNull()));
  }

  @Test
  public void testGetServiceEndpointNoImplementation(TestContext context) throws IOException {
    usageDataProvider.getHarvestingConfig().getSushiConfig().setServiceType("test3");
    new ServiceEndpointFactory(provider -> null)
        .createServiceEndpoint(usageDataProvider)
        .onComplete(
            context.asyncAssertFailure(
                t -> assertThat(t).hasMessageContaining("No service implementation")));
  }

  @Test
  public void testGetServiceEndpointAggregator(TestContext context) throws IOException {
    usageDataProvider.getHarvestingConfig().setHarvestVia(HarvestVia.AGGREGATOR);
    AggregatorSetting aggregatorSetting =
        Json.decodeValue(
            Resources.toString(
                Resources.getResource("__files/aggregator-setting.json"), StandardCharsets.UTF_8),
            AggregatorSetting.class);
    new ServiceEndpointFactory(provider -> succeededFuture(aggregatorSetting))
        .createServiceEndpoint(usageDataProvider)
        .onComplete(context.asyncAssertSuccess(sep -> assertThat(sep).isNotNull()));
  }

  @Test
  public void testGetServiceEndpointAggregatorNull(TestContext context) {
    usageDataProvider.getHarvestingConfig().setHarvestVia(HarvestVia.AGGREGATOR);
    usageDataProvider.getHarvestingConfig().setAggregator(null);

    new ServiceEndpointFactory(provider -> null)
        .createServiceEndpoint(usageDataProvider)
        .onComplete(context.asyncAssertSuccess(sep -> assertThat(sep).isNotNull()));
  }

  @Test
  public void testGetServiceEndpointAggregatorIdNull(TestContext context) {
    usageDataProvider.getHarvestingConfig().setHarvestVia(HarvestVia.AGGREGATOR);
    usageDataProvider.getHarvestingConfig().getAggregator().setId(null);

    new ServiceEndpointFactory(provider -> null)
        .createServiceEndpoint(usageDataProvider)
        .onComplete(context.asyncAssertSuccess(sep -> assertThat(sep).isNotNull()));
  }
}
