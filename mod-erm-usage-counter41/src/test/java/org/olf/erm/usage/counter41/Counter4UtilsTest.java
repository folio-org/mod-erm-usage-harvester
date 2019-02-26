package org.olf.erm.usage.counter41;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.JAXB;
import org.junit.BeforeClass;
import org.junit.Test;
import org.niso.schemas.counter.Report;
import org.niso.schemas.counter.Report.Customer;
import org.niso.schemas.counter.ReportItem;
import org.olf.erm.usage.counter41.Counter4Utils.ReportMergeException;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;

public class Counter4UtilsTest {

  private static String json1;
  private static String json2;

  @BeforeClass
  public static void init() throws IOException {
    json1 = Resources.toString(Resources.getResource("merge/json1.json"), StandardCharsets.UTF_8);
    json2 = Resources.toString(Resources.getResource("merge/json2.json"), StandardCharsets.UTF_8);
  }

  @Test
  public void testConversions() throws IOException, URISyntaxException {
    File file = new File(Resources.getResource("reportJSTOR.xml").getFile());

    Report fromXML = JAXB.unmarshal(file, Report.class);
    Report fromXML2 =
        Counter4Utils.fromString(Files.asCharSource(file, StandardCharsets.UTF_8).read());
    Report fromJSON = Counter4Utils.fromJSON(Counter4Utils.toJSON(fromXML));
    Report fromJSON2 = Counter4Utils.fromJSON(Counter4Utils.mapper.writeValueAsString(fromXML));

    assertThat(fromJSON).isEqualToComparingFieldByFieldRecursively(fromXML);
    assertThat(fromJSON).isEqualToComparingFieldByFieldRecursively(fromJSON2);
    assertThat(fromJSON).isEqualToComparingFieldByFieldRecursively(fromXML2);
  }

  @Test
  public void testGetNameForReportTitle() {
    assertThat(Counter4Utils.getNameForReportTitle("Journal Report 1 (R4)")).isEqualTo("JR1");
    assertThat(Counter4Utils.getNameForReportTitle("Journal Report 1)")).isEqualTo("JR1");
    assertThat(Counter4Utils.getNameForReportTitle("JR1")).isEqualTo("JR1");
    assertThat(Counter4Utils.getNameForReportTitle("some title with JR1")).isEqualTo("JR1");
    assertThat(Counter4Utils.getNameForReportTitle("")).isEqualTo(null);
    assertThat(Counter4Utils.getNameForReportTitle("a title that does not exist")).isEqualTo(null);
  }

  @Test
  public void testGetTitlesForReportName() {
    assertThat(Counter4Utils.getTitlesForReportName("JR1"))
        .isEqualTo(Arrays.asList("JR1", "Journal Report 1"));
    assertThat(Counter4Utils.getTitlesForReportName("")).isEqualTo(null);
    assertThat(Counter4Utils.getTitlesForReportName("a report name that does not exist"))
        .isEqualTo(null);
  }

  @Test
  public void testMergeReports() throws ReportMergeException {
    Report rep1 = Counter4Utils.fromJSON(new JsonObject(json1).getJsonObject("report").encode());
    Report rep2 = Counter4Utils.fromJSON(new JsonObject(json2).getJsonObject("report").encode());

    Report merge = Counter4Utils.merge(rep1, rep2);
    assertThat(merge.getCustomer()).isNotEmpty();
    List<ReportItem> reportItems = merge.getCustomer().get(0).getReportItems();
    assertThat(reportItems.size()).isEqualTo(3);
    assertThat(reportItems.get(0).getItemPerformance().size()).isEqualTo(2);
  }

  @Test
  public void testMergeReportsAttributesDontMatch() {
    Report rep1 = Counter4Utils.fromJSON(new JsonObject(json1).getJsonObject("report").encode());
    Report rep2 =
        Counter4Utils.fromJSON(
            new JsonObject(json2).getJsonObject("report").put("version", "5").encode());

    assertThatThrownBy(() -> Counter4Utils.merge(rep1, rep2))
        .isInstanceOf(ReportMergeException.class)
        .hasMessageContaining("attributes do not match");
  }

  @Test
  public void testMergeReportsInvalidCustomer() {
    Report rep1 = Counter4Utils.fromJSON(new JsonObject(json1).getJsonObject("report").encode());
    Report rep2 = Counter4Utils.fromJSON(new JsonObject(json2).getJsonObject("report").encode());
    rep2.getCustomer().add(new Customer());

    assertThatThrownBy(() -> Counter4Utils.merge(rep1, rep2))
        .isInstanceOf(ReportMergeException.class)
        .hasMessageContaining("invalid customer definitions");
  }
}
