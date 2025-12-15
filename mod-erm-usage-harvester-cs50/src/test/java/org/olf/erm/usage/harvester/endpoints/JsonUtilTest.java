package org.olf.erm.usage.harvester.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.olf.erm.usage.harvester.endpoints.JsonUtil.isJsonArray;
import static org.olf.erm.usage.harvester.endpoints.JsonUtil.isOfType;

import com.google.common.io.Resources;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openapitools.counter50.model.COUNTERDatabaseReport;
import org.openapitools.counter50.model.COUNTERItemReport;
import org.openapitools.counter50.model.COUNTERPlatformReport;
import org.openapitools.counter50.model.COUNTERTitleReport;
import org.openapitools.counter50.model.SUSHIErrorModel;

@RunWith(VertxUnitRunner.class)
public class JsonUtilTest {

  @Test
  public void testIsOfType() throws IOException {
    String sampleReport =
        Resources.toString(Resources.getResource("SampleReport.json"), StandardCharsets.UTF_8);
    String sampleReportMissingHeader =
        Resources.toString(
            Resources.getResource("SampleReportMissingHeader.json"), StandardCharsets.UTF_8);
    String sampleReportMissingItems =
        Resources.toString(
            Resources.getResource("SampleReportMissingItems.json"), StandardCharsets.UTF_8);
    String sampleReportEmptyItems =
        Resources.toString(
            Resources.getResource("SampleReportEmptyItems.json"), StandardCharsets.UTF_8);

    // assertThat(isOfType("{}", SUSHIErrorModel.class)).isFalse();
    assertThat(isOfType(sampleReport, SUSHIErrorModel.class)).isFalse();
    assertThat(isOfType(sampleReport, COUNTERDatabaseReport.class)).isFalse();
    assertThat(isOfType(sampleReport, COUNTERPlatformReport.class)).isFalse();
    assertThat(isOfType(sampleReport, COUNTERItemReport.class)).isFalse();
    assertThat(isOfType(sampleReport, COUNTERTitleReport.class)).isTrue();
    assertThat(isOfType(sampleReportMissingHeader, COUNTERTitleReport.class)).isTrue();
    assertThat(isOfType(sampleReportMissingItems, COUNTERTitleReport.class)).isTrue();
    assertThat(isOfType(sampleReportEmptyItems, COUNTERTitleReport.class)).isTrue();

    String error = Resources.toString(Resources.getResource("error.json"), StandardCharsets.UTF_8);
    String errorarray =
        Resources.toString(Resources.getResource("errorarray.json"), StandardCharsets.UTF_8);
    String erroravailablereports =
        Resources.toString(
            Resources.getResource("erroravailablereports.json"), StandardCharsets.UTF_8);
    assertThat(isOfType(error, SUSHIErrorModel.class)).isTrue();
    assertThat(isOfType(errorarray, SUSHIErrorModel.class)).isFalse();
    assertThat(isOfType(erroravailablereports, SUSHIErrorModel.class)).isFalse();
    assertThat(isOfType(erroravailablereports, COUNTERTitleReport.class)).isFalse();
  }

  @Test
  public void testIsJsonArray() throws IOException {
    String error = Resources.toString(Resources.getResource("error.json"), StandardCharsets.UTF_8);
    String errorarray =
        Resources.toString(Resources.getResource("errorarray.json"), StandardCharsets.UTF_8);
    String erroravailablereports =
        Resources.toString(
            Resources.getResource("erroravailablereports.json"), StandardCharsets.UTF_8);

    assertThat(isJsonArray(null)).isFalse();
    assertThat(isJsonArray(error)).isFalse();
    assertThat(isJsonArray(errorarray)).isTrue();
    assertThat(isJsonArray(erroravailablereports)).isTrue();
  }
}
