package org.olf.erm.usage.counter41;

import java.io.IOException;
import javax.xml.datatype.XMLGregorianCalendar;
import org.niso.schemas.counter.Report;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class Counter4Utils {

  private static ObjectMapper mapper = createObjectMapper();

  public static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(new XMLGregorianCalendarSerializer(XMLGregorianCalendar.class));
    module.addDeserializer(XMLGregorianCalendar.class,
        new XMLGregorianCalendarDeserializer(XMLGregorianCalendar.class));
    mapper.registerModule(module);
    mapper.setSerializationInclusion(Include.NON_NULL);
    return mapper;
  }

  public static String toJSON(Report report) {
    String str = "";
    try {
      str = mapper.writeValueAsString(report);
    } catch (JsonProcessingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return str;
  }

  public static Report fromJSON(String json) {
    Report result = null;
    try {
      result = mapper.readValue(json, Report.class);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return result;
  }

  private Counter4Utils() {}
}
