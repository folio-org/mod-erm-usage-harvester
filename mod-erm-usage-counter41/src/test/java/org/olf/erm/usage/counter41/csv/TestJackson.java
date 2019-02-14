package org.olf.erm.usage.counter41.csv;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import javax.xml.bind.JAXB;
import org.folio.rest.jaxrs.model.CounterReport;
import org.junit.Test;
import org.niso.schemas.counter.Report;
import org.niso.schemas.sushi.counter.CounterReportResponse;
import org.olf.erm.usage.counter41.Counter4Utils;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.json.Json;

public class TestJackson {

  @Test
  public void testC4XML() throws URISyntaxException {
    File file = new File(Resources.getResource("NSSReport2018-01-2018-03.xml").toURI());
    CounterReportResponse counterReportResponse = JAXB.unmarshal(file, CounterReportResponse.class);

    Report report = counterReportResponse.getReport().getReport().get(0);
    String s = CSVMapper.toCSV(report);
    System.out.println(s);
  }

  @Test
  public void testC4Json() throws IOException {
    String crStr = Resources.toString(Resources.getResource("jstor2019-01.json"), Charsets.UTF_8);
    CounterReport counterReport = Json.decodeValue(crStr, CounterReport.class);

    Report report = Counter4Utils.fromJSON(Json.encode(counterReport.getReport()));
    String s = CSVMapper.toCSV(report);
    System.out.println(s);
  }
}
