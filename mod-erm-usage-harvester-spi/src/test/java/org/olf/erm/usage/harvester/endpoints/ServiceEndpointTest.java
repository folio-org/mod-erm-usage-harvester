package org.olf.erm.usage.harvester.endpoints;

import java.io.File;
import java.io.IOException;
import org.folio.rest.jaxrs.model.AggregatorSetting;
import org.folio.rest.jaxrs.model.UsageDataProvider;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ServiceEndpointTest {

  @Rule
  public RunTestOnContext ctx = new RunTestOnContext();

  private static UsageDataProvider provider;
  private static AggregatorSetting aggregator;

  @BeforeClass
  public static void setup(TestContext context)
      throws JsonParseException, JsonMappingException, IOException {
    provider = new ObjectMapper().readValue(
        new File(Resources.getResource("__files/usage-data-provider.json").getFile()),
        UsageDataProvider.class);
    aggregator = new ObjectMapper().readValue(
        new File(Resources.getResource("__files/aggregator-setting.json").getFile()),
        AggregatorSetting.class);
  }

  // @Test
  // public void createNSS(TestContext context) {
  // ServiceEndpoint sep = ServiceEndpoint.create(provider, aggregator.withServiceType("NSS"));
  // context.assertTrue(sep instanceof NSS);
  //
  // ServiceEndpoint sep2 = ServiceEndpoint.create(provider.withServiceType("NSS"), null);
  // context.assertTrue(sep2 instanceof NSS);
  // }

  @Test
  public void createNoImpl(TestContext context) {
    ServiceEndpoint sep = ServiceEndpoint.create(provider, aggregator.withServiceType(""));
    context.assertTrue(sep == null);

    ServiceEndpoint sep2 = ServiceEndpoint.create(provider.withServiceType(""), null);
    context.assertTrue(sep2 == null);
  }
}
