package org.olf.erm.usage.counter41;

import static com.google.common.base.MoreObjects.toStringHelper;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.datatype.XMLGregorianCalendar;
import org.niso.schemas.counter.Report;
import org.niso.schemas.sushi.Exception;
import org.niso.schemas.sushi.ExceptionSeverity;
import org.niso.schemas.sushi.counter.CounterReportResponse;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
    module.addDeserializer(
        XMLGregorianCalendar.class,
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

  public static List<Exception> getExceptions(CounterReportResponse response) {
    return response
        .getException()
        .stream()
        .filter(
            e ->
                e.getSeverity().equals(ExceptionSeverity.ERROR)
                    || e.getSeverity().equals(ExceptionSeverity.FATAL))
        .collect(Collectors.toList());
  }

  public static String getErrorMessages(List<Exception> exs) {
    return exs.stream()
        .map(
            e -> {
              String data = null;
              if (e.getData() != null && e.getData().getValue() instanceof Element) {
                Node n = ((Element) e.getData().getValue()).getFirstChild();
                if (n != null && !n.getTextContent().isEmpty()) data = n.getTextContent();
              }
              String helpUrl =
                  (e.getHelpUrl() == null || e.getHelpUrl().getValue().isEmpty())
                      ? null
                      : e.getHelpUrl().getValue();
              return toStringHelper(e)
                  .add("Number", e.getNumber())
                  .add("Severity", e.getSeverity())
                  .add("Message", e.getMessage())
                  .add("HelpUrl", helpUrl)
                  .add("Data", data)
                  .omitNullValues()
                  .toString();
            })
        .collect(Collectors.joining(", "));
  }

  private Counter4Utils() {}
}
