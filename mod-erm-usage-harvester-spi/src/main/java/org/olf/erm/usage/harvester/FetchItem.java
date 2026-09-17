package org.olf.erm.usage.harvester;

import static org.olf.erm.usage.harvester.DateUtil.getYearMonths;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

public record FetchItem(String reportType, String begin, String end) {

  public FetchItem {
    Objects.requireNonNull(reportType, "reportType cannot be null");
    Objects.requireNonNull(begin, "begin cannot be null");
    Objects.requireNonNull(end, "end cannot be null");
  }

  public static FetchItem of(final String reportType, final List<YearMonth> monthRange) {
    return new FetchItem(
        reportType,
        monthRange.getFirst().atDay(1).toString(),
        monthRange.getLast().atEndOfMonth().toString());
  }

  public static FetchItem of(final String reportType, final YearMonth monthRange) {
    return new FetchItem(
        reportType, monthRange.atDay(1).toString(), monthRange.atEndOfMonth().toString());
  }

  public static FetchItem of(final String reportType, final YearMonth start, final YearMonth end) {
    return new FetchItem(reportType, start.atDay(1).toString(), end.atEndOfMonth().toString());
  }

  /**
   * @return The expanded fetch item list in case 'start' and 'end' span multiple months
   */
  public List<FetchItem> expand() {
    final var months = getYearMonths(begin, end);
    return months.stream().map(ym -> FetchItem.of(reportType, ym)).toList();
  }
}
