package org.olf.erm.usage.counter41;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.bind.JAXB;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.junit.Test;
import org.niso.schemas.counter.Report;
import org.olf.erm.usage.counter41.Counter4Utils;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SerializationTest {

  private static ObjectMapper mapper = Counter4Utils.createObjectMapper();

  public void testXMLGregCalString(String datetime) {
    try {
      XMLGregorianCalendar date = DatatypeFactory.newInstance().newXMLGregorianCalendar(datetime);
      String str = mapper.writeValueAsString(date);
      XMLGregorianCalendar cal = mapper.readValue(str, XMLGregorianCalendar.class);
      assertThat(date.toString()).isEqualTo(datetime);
      assertThat(str).isEqualTo(mapper.writeValueAsString(datetime));
      assertThat(cal.toString()).isEqualTo(datetime);
    } catch (IOException | DatatypeConfigurationException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testXMLGregCalStrings() {
    // +00:00 gets replaced by Z
    testXMLGregCalString("2018-10-24T08:37:25.730+01:00");
    testXMLGregCalString("2018-10-24");
    testXMLGregCalString("2018-10-24T08:37:25.730Z");
    testXMLGregCalString("2018-10-24T12:40:00.000Z");
  }

  @Test
  public void testSampleReport() throws IOException {
    InputStream is = this.getClass().getClassLoader().getResource("reportJSTOR.xml").openStream();
    Report report = JAXB.unmarshal(is, Report.class);

    String json = Counter4Utils.toJSON(report);
    Report fromJSON = Counter4Utils.fromJSON(json);

    assertThat(report).isEqualToComparingFieldByFieldRecursively(fromJSON);
  }
}
