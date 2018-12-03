package org.olf.erm.usage.harvester;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.google.common.base.Strings;

public class DateUtil {

  public static YearMonth getMaxMonth() {
    return YearMonth.now().minusMonths(1); // FIXME: parameterize
  }

  public static YearMonth getStartMonth(String startStr) {
    return YearMonth.parse(startStr);
  }

  public static YearMonth getEndMonth(String endStr) {
    YearMonth max = getMaxMonth();
    YearMonth end = (Objects.isNull(Strings.emptyToNull(endStr))) ? max : YearMonth.parse(endStr);
    return end.isAfter(max) ? max : end;
  }

  public static List<YearMonth> getYearMonths(String startStr, String endStr) {
    Objects.requireNonNull(Strings.emptyToNull(startStr));

    YearMonth start = getStartMonth(startStr);
    YearMonth end = getEndMonth(endStr);

    List<YearMonth> resultList = new ArrayList<>();
    YearMonth temp = YearMonth.from(start);
    while (temp.isBefore(end) || temp.equals(end)) {
      resultList.add(YearMonth.from(temp));
      temp = temp.plusMonths(1);
    }

    return resultList;
  }

  private DateUtil() {}
}
