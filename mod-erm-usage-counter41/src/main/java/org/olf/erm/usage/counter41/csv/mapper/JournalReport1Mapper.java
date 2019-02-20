package org.olf.erm.usage.counter41.csv.mapper;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.niso.schemas.counter.MetricType;
import org.niso.schemas.counter.PerformanceCounter;
import org.niso.schemas.counter.Report;
import org.niso.schemas.counter.ReportItem;
import org.olf.erm.usage.counter41.Counter4Utils;
import org.olf.erm.usage.counter41.csv.cellprocessor.IdentifierProcessor;
import org.olf.erm.usage.counter41.csv.cellprocessor.MonthPerformanceProcessor;
import org.olf.erm.usage.counter41.csv.cellprocessor.ReportingPeriodProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvListWriter;
import org.supercsv.io.dozer.CsvDozerBeanWriter;
import org.supercsv.io.dozer.ICsvDozerBeanWriter;
import org.supercsv.prefs.CsvPreference;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

public class JournalReport1Mapper {

  private static final Logger LOG = LoggerFactory.getLogger(JournalReport1Mapper.class);
  private final Report report;
  private final List<YearMonth> YEAR_MONTHS;
  private final String[] FIELD_MAPPING;
  private final CellProcessor[] PROCESSORS;
  private final String[] HEADER;
  private final String[] TOTALS_LINE;

  public JournalReport1Mapper(Report report) {
    this.report = report;
    this.YEAR_MONTHS = getYearMonths();
    this.FIELD_MAPPING = createFieldMapping();
    this.PROCESSORS = createProcessors();
    this.HEADER = createHeader();
    this.TOTALS_LINE = createTotals();
  }

  private String[] createHeader() {
    List<String> first =
        Arrays.asList(
            "Journal",
            "Publisher",
            "Platform",
            "Journal DOI",
            "Proprietary Identifier",
            "Print ISSN",
            "Online ISSN",
            "Reporting Period Total",
            "Reporting Period HTML",
            "Reporting Period PDF");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM-uuuu", Locale.ENGLISH);
    Stream<String> months = YEAR_MONTHS.stream().map(ym -> ym.format(formatter));
    return Stream.concat(first.stream(), months).toArray(String[]::new);
  }

  private String[] createTotals() {
    List<String> first =
        Arrays.asList(
            "Total for all journals",
            Strings.emptyToNull(getSinglePublisher()),
            Strings.emptyToNull(getSinglePlatform()),
            null,
            null,
            null,
            null,
            bigIntToStringOrNull(getPeriodMetricTotal(MetricType.FT_TOTAL, null)),
            bigIntToStringOrNull(getPeriodMetricTotal(MetricType.FT_HTML, null)),
            bigIntToStringOrNull(getPeriodMetricTotal(MetricType.FT_PDF, null)));
    Stream<String> rest =
        YEAR_MONTHS
            .stream()
            .map(ym -> bigIntToStringOrNull(getPeriodMetricTotal(MetricType.FT_TOTAL, ym)));
    return Stream.concat(first.stream(), rest).toArray(String[]::new);
  }

  private CellProcessor[] createProcessors() {
    List<Optional> first =
        Arrays.asList(
            new Optional(), // Journal
            new Optional(), // Publisher
            new Optional(), // Platform
            new Optional(), // Journal DOI
            new Optional(), // Proprietary Identifier
            new Optional(new IdentifierProcessor("Print_ISSN")), // Print ISSN
            new Optional(new IdentifierProcessor("Online_ISSN")), // Online ISSN
            new Optional(
                new ReportingPeriodProcessor(MetricType.FT_TOTAL)), // Reporting Period Total
            new Optional(new ReportingPeriodProcessor(MetricType.FT_HTML)), // Reporting Period HTML
            new Optional(new ReportingPeriodProcessor(MetricType.FT_PDF)) // Reporting Period PDF
            );
    Stream<Optional> rest =
        YEAR_MONTHS.stream().map(ym -> new Optional(new MonthPerformanceProcessor(ym)));
    return Stream.concat(first.stream(), rest).toArray(CellProcessor[]::new);
  }

  private String[] createFieldMapping() {
    List<String> first =
        Arrays.asList(
            "itemName", // Journal
            "itemPublisher", // Publisher
            "itemPlatform", // Platform
            "itemPlatform", // Journal DOI
            "itemPlatform", // Proprietary Identifier
            "itemIdentifier", // Print ISSN
            "itemIdentifier" // Online ISSN
            );
    return Stream.concat(
            first.stream(), Collections.nCopies(YEAR_MONTHS.size() + 3, "itemPerformance").stream())
        .toArray(String[]::new);
  }

  private String getSinglePublisher() {
    List<String> uniquePublishers =
        report
            .getCustomer()
            .get(0)
            .getReportItems()
            .stream()
            .map(ReportItem::getItemPublisher)
            .distinct()
            .collect(Collectors.toList());
    LOG.info(String.format("Found %s publishers: %s", uniquePublishers.size(), uniquePublishers));
    return (uniquePublishers.size() == 1) ? uniquePublishers.get(0) : null;
  }

  private String getSinglePlatform() {
    List<String> uniquePlatforms =
        report
            .getCustomer()
            .get(0)
            .getReportItems()
            .stream()
            .map(ReportItem::getItemPlatform)
            .distinct()
            .collect(Collectors.toList());
    LOG.info(String.format("Found %s platforms: %s", uniquePlatforms.size(), uniquePlatforms));
    return (uniquePlatforms.size() == 1) ? uniquePlatforms.get(0) : null;
  }

  private BigInteger getPeriodMetricTotal(MetricType metricType, YearMonth month) {
    return report
        .getCustomer()
        .get(0)
        .getReportItems()
        .stream()
        .flatMap(ri -> ri.getItemPerformance().stream())
        .filter(
            ip ->
                month == null
                    || (ip.getPeriod()
                            .getBegin()
                            .toGregorianCalendar()
                            .toZonedDateTime()
                            .toLocalDate()
                            .equals(month.atDay(1))
                        && ip.getPeriod()
                            .getEnd()
                            .toGregorianCalendar()
                            .toZonedDateTime()
                            .toLocalDate()
                            .equals(month.atEndOfMonth())))
        .flatMap(m -> m.getInstance().stream())
        .filter(pc -> pc.getMetricType().equals(metricType))
        .map(PerformanceCounter::getCount)
        .reduce((a, b) -> a.add(b))
        .orElse(null);
  }

  /**
   * Returns a seamless list of every month that falls within the reporting period.
   *
   * @return list of {@code YearMonth}
   */
  private List<YearMonth> getYearMonths() {
    List<YearMonth> uniqueSortedYearMonths = Counter4Utils.getYearMonthsFromReport(report);
    if (uniqueSortedYearMonths.isEmpty()) {
      return uniqueSortedYearMonths;
    } else {
      YearMonth first = uniqueSortedYearMonths.get(0);
      YearMonth last = uniqueSortedYearMonths.get(uniqueSortedYearMonths.size() - 1);
      long diff = first.until(last, ChronoUnit.MONTHS);
      return Stream.iterate(first, next -> next.plusMonths(1))
          .limit(diff + 1)
          .collect(Collectors.toList());
    }
  }

  private String bigIntToStringOrNull(BigInteger bigint) {
    return (bigint == null) ? null : String.valueOf(bigint);
  }

  public String toCsv() {
    StringWriter stringWriter = new StringWriter();

    // write unformatted stuff with CsvListWriter

    try (CsvListWriter csvListWriter =
        new CsvListWriter(stringWriter, CsvPreference.STANDARD_PREFERENCE)) {
      csvListWriter.write(
          "Journal Report 1(R4)",
          "Number of Successful Full-Text Article Requests by Month and Journal");
      csvListWriter.write(report.getCustomer().get(0).getID());
      csvListWriter.write(
          ""); // FIXME: Cell A3 contains the “Institutional Identifier” as defined in Appendix A,
      // but may be left blank if the vendor does not use Institutional Identifiers
      csvListWriter.write("Period covered by Report");
      csvListWriter.write(
          Iterables.getFirst(YEAR_MONTHS, null).atDay(1).toString()
              + " to "
              + Iterables.getLast(YEAR_MONTHS).atEndOfMonth().toString());
      csvListWriter.write("Date run");
      csvListWriter.write(LocalDate.now());
      csvListWriter.flush();
    } catch (IOException e) {
      LOG.error(e.getMessage(), e);
      return null;
    }

    // and the formatted stuff with CsvDozerBeanWriter
    try (ICsvDozerBeanWriter beanWriter =
        new CsvDozerBeanWriter(stringWriter, CsvPreference.STANDARD_PREFERENCE)) {

      // configure the mapping from the fields to the CSV columns
      beanWriter.configureBeanMapping(ReportItem.class, FIELD_MAPPING);

      // write the header
      beanWriter.writeHeader(HEADER);

      // write totals line
      beanWriter.writeHeader(TOTALS_LINE);

      // write journals
      for (final ReportItem item : report.getCustomer().get(0).getReportItems()) {
        beanWriter.write(item, PROCESSORS);
      }

      beanWriter.flush();
      return stringWriter.toString();
    } catch (IOException e) {
      LOG.error(e.getMessage(), e);
      return null;
    }
  }
}
