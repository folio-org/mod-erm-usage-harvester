package org.olf.erm.usage.counter41.csv.cellprocessor;

import java.math.BigInteger;
import java.time.YearMonth;
import java.util.ArrayList;
import org.niso.schemas.counter.Metric;
import org.niso.schemas.counter.MetricType;
import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.util.CsvContext;

public class MonthPerformanceProcessor extends CellProcessorAdaptor {

  private YearMonth yearMonth;

  public MonthPerformanceProcessor(YearMonth yearMonth) {
    super();
    this.yearMonth = yearMonth;
  }

  @SuppressWarnings("unchecked")
  @Override
  public BigInteger execute(Object value, CsvContext csvContext) {
    return ((ArrayList<Metric>) value)
        .stream()
        .filter(
            m ->
                m.getPeriod()
                        .getBegin()
                        .toGregorianCalendar()
                        .toZonedDateTime()
                        .toLocalDate()
                        .equals(yearMonth.atDay(1))
                    && m.getPeriod()
                        .getEnd()
                        .toGregorianCalendar()
                        .toZonedDateTime()
                        .toLocalDate()
                        .equals(yearMonth.atEndOfMonth()))
        .findFirst()
        .map(
            m ->
                m.getInstance()
                    .stream()
                    .filter(pc -> pc.getMetricType().equals(MetricType.FT_TOTAL))
                    .findFirst())
        .map(pc -> pc.get().getCount())
        .orElse(null);
  }
}
