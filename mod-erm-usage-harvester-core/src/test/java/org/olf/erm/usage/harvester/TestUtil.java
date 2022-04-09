package org.olf.erm.usage.harvester;

import java.util.Arrays;
import org.folio.rest.jaxrs.model.HarvestingConfig;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestVia;
import org.folio.rest.jaxrs.model.HarvestingConfig.HarvestingStatus;
import org.folio.rest.jaxrs.model.SushiConfig;
import org.folio.rest.jaxrs.model.SushiCredentials;
import org.folio.rest.jaxrs.model.UsageDataProvider;

public class TestUtil {

  public static UsageDataProvider createSampleUsageDataProvider() {
    String uuid = "97329ea7-f351-458a-a460-71aa6db75e35";
    return new UsageDataProvider()
        .withId(uuid)
        .withLabel("TestProvider")
        .withSushiCredentials(new SushiCredentials().withCustomerId("Customer123"))
        .withHarvestingConfig(
            new HarvestingConfig()
                .withHarvestingStatus(HarvestingStatus.ACTIVE)
                .withHarvestVia(HarvestVia.SUSHI)
                .withSushiConfig(new SushiConfig().withServiceType("test1"))
                .withReportRelease(4)
                .withHarvestingStart("2017-12")
                .withHarvestingEnd("2018-04")
                .withRequestedReports(Arrays.asList("JR1", "JR2", "JR3")));
  }

  private TestUtil() {
  }
}
