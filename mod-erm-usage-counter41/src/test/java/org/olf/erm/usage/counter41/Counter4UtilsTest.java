package org.olf.erm.usage.counter41;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.xml.bind.JAXB;
import org.junit.Test;
import org.niso.schemas.counter.Report;
import com.google.common.io.Files;
import com.google.common.io.Resources;

public class Counter4UtilsTest {

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
}
