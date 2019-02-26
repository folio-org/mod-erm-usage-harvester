package org.olf.erm.usage.counter41;

import static com.google.common.base.MoreObjects.toStringHelper;
import java.io.IOException;
import java.io.StringReader;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.bind.JAXB;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.commons.lang3.SerializationUtils;
import org.niso.schemas.counter.Report;
import org.niso.schemas.counter.ReportItem;
import org.niso.schemas.sushi.Exception;
import org.niso.schemas.sushi.ExceptionSeverity;
import org.niso.schemas.sushi.counter.CounterReportResponse;
import org.olf.erm.usage.counter41.csv.CSVMapper;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class Counter4Utils {

  public static final ObjectMapper mapper = createObjectMapper();
  private static Map<String, List<String>> mappingEntries = new HashMap<>();

  static {
    mappingEntries.put("JR1", Arrays.asList("JR1", "Journal Report 1"));
  }

  public static List<String> getTitlesForReportName(String reportName) {
    return mappingEntries.get(reportName);
  }

  public static String getNameForReportTitle(String title) {
    return mappingEntries
        .entrySet()
        .stream()
        .filter(e -> e.getValue().stream().anyMatch(title::contains))
        .findFirst()
        .map(Entry::getKey)
        .orElse(null);
  }

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
    String str = null;
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

  public static String toCSV(Report report) {
    return CSVMapper.toCSV(report);
  }

  // TODO: check that report includes one month only for now
  public static Report fromString(String content) {
    try {
      CounterReportResponse crr =
          JAXB.unmarshal(new StringReader(content), CounterReportResponse.class);
      return crr.getReport().getReport().get(0);
    } catch (java.lang.Exception e) {
      try {
        return JAXB.unmarshal(new StringReader(content), Report.class);
      } catch (java.lang.Exception e1) {
        return null;
      }
    }
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

  public static List<YearMonth> getYearMonthsFromReport(Report report) {
    return report
        .getCustomer()
        .get(0)
        .getReportItems()
        .stream()
        .flatMap(ri -> ri.getItemPerformance().stream())
        .flatMap(
            m ->
                Stream.<YearMonth>of(
                    YearMonth.of(
                        m.getPeriod().getBegin().getYear(), m.getPeriod().getBegin().getMonth()),
                    YearMonth.of(
                        m.getPeriod().getEnd().getYear(), m.getPeriod().getEnd().getMonth())))
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  public static class ReportMergeException extends java.lang.Exception {

    private static final long serialVersionUID = 1L;

    public ReportMergeException() {}

    public ReportMergeException(String message) {
      super(message);
    }
  }

  public static Report merge(Collection<Report> c) throws ReportMergeException {
    return merge(c.toArray(new Report[0]));
  }

  public static Report merge(Report... reports) throws ReportMergeException {
    Report[] clonedReports = SerializationUtils.clone(reports);

    if (!Stream.of(clonedReports).allMatch(r -> r.getCustomer().size() == 1)) {
      throw new ReportMergeException(
          "At least one report contains invalid customer definitions (expecting one customer per report)");
    }

    // check that provided reports have the same attributes
    if (Stream.of(clonedReports)
            .map(
                r -> {
                  // reset some attributes for equals() check
                  r.getCustomer().get(0).getReportItems().clear();
                  r.getCreated().clear();
                  r.setID(null);
                  return r;
                })
            .distinct()
            .count()
        != 1) throw new ReportMergeException("Report attributes do not match");

    List<ReportItem> sortedCombinedReportItems =
        Stream.of(reports)
            .flatMap(r -> r.getCustomer().get(0).getReportItems().stream())
            .collect(
                Collectors.toMap(
                    ReportItem::getItemIdentifier,
                    ri -> ri,
                    (a, b) -> {
                      a.getItemPerformance().addAll(b.getItemPerformance());
                      return a;
                    }))
            .values()
            .stream()
            .sorted((r1, r2) -> r1.getItemName().compareTo(r2.getItemName()))
            .collect(Collectors.toList());

    clonedReports[0].getCustomer().get(0).getReportItems().addAll(sortedCombinedReportItems);
    return clonedReports[0];
  }

  private Counter4Utils() {}
}
