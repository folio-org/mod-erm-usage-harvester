package org.olf.erm.usage.counter41.csv;

import java.util.stream.Stream;
import org.niso.schemas.counter.Report;
import org.olf.erm.usage.counter41.csv.mapper.JournalReport1Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CSVMapper {

  private static final Logger LOG = LoggerFactory.getLogger(CSVMapper.class);
  private static final String[] JR1 = new String[] {"JR1", "Journal Report 1"};

  public static String toCSV(Report report) {
    if (Stream.of(JR1).anyMatch(s -> report.getTitle().contains(s))
        && report.getVersion().equals("4")) {
      return new JournalReport1Mapper(report).toCsv();
    } else {
      LOG.error(
          String.format(
              "No mapping found for report title '%s' and version '%s'",
              report.getTitle(), report.getVersion()));
      return null;
    }
  }

  private CSVMapper() {}
}
