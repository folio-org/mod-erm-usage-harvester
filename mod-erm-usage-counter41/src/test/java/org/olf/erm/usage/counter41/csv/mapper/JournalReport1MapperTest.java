package org.olf.erm.usage.counter41.csv.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import javax.xml.bind.JAXB;
import org.junit.Test;
import org.niso.schemas.counter.Report;
import org.niso.schemas.sushi.counter.CounterReportResponse;
import com.google.common.io.Resources;

public class JournalReport1MapperTest {

  @Test
  public void testC4Json() throws URISyntaxException, IOException {
    File file = new File(Resources.getResource("JR1/NSSReport2018-01-2018-03.xml").toURI());
    String expected =
        new String(Resources.toByteArray(Resources.getResource("JR1/NSSReport2018-01-2018-03.csv")))
            .replace("$$$date_run$$$", LocalDate.now().toString());
    Report report =
        JAXB.unmarshal(file, CounterReportResponse.class).getReport().getReport().get(0);
    JournalReport1Mapper journalReport1Mapper = new JournalReport1Mapper(report);

    String result = journalReport1Mapper.toCsv();
    assertThat(result).isEqualToIgnoringNewLines(expected);
  }
}
