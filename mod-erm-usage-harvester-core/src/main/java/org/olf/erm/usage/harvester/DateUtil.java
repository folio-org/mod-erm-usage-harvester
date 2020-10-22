package org.olf.erm.usage.harvester;

import com.google.common.base.Strings;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DateUtil {

  private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM[-dd]");

  public static YearMonth getYearMonthFromString(String dateStr) {
    Objects.requireNonNull(dateStr);
    return YearMonth.parse(dateStr, dtf);
  }

  public static YearMonth getYearMonthFromStringWithLimit(String dateStr, YearMonth maxYearMonth) {
    Objects.requireNonNull(maxYearMonth);
    YearMonth end = (Strings.isNullOrEmpty(dateStr)) ? maxYearMonth : YearMonth.parse(dateStr, dtf);
    return end.isAfter(maxYearMonth) ? maxYearMonth : end;
  }

  public static List<YearMonth> getYearMonths(YearMonth start, YearMonth end) {
    Objects.requireNonNull(start);
    Objects.requireNonNull(end);
    List<YearMonth> resultList = new ArrayList<>();
    YearMonth temp = YearMonth.from(start);
    while (temp.isBefore(end) || temp.equals(end)) {
      resultList.add(YearMonth.from(temp));
      temp = temp.plusMonths(1);
    }
    return resultList;
  }

  public static List<YearMonth> getYearMonths(String startStr, String endStr) {
    Objects.requireNonNull(startStr);
    Objects.requireNonNull(endStr);
    return getYearMonths(getYearMonthFromString(startStr), getYearMonthFromString(endStr));
  }

  private DateUtil() {}
}
