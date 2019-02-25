package org.olf.erm.usage.counter41;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import com.google.common.io.Resources;

public class Counter4UtilsTest {

  @Test
  public void testFromString() throws IOException, URISyntaxException {
    String content =
        IOUtils.toString(Resources.getResource("reportJSTOR.xml").toURI(), StandardCharsets.UTF_8);
    System.out.println(content);
    String string = Counter4Utils.toJSON(Counter4Utils.fromString(content));
    System.out.println(string);
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
