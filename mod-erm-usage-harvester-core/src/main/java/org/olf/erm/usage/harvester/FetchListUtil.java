package org.olf.erm.usage.harvester;

import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Iterables.getLast;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FetchListUtil {

  public static final int MAX_RANGE = 12;

  private FetchListUtil() {}

  public static FetchItem createFetchItemFromYearMonth(String reportType, YearMonth yearMonth) {
    Objects.requireNonNull(reportType);
    Objects.requireNonNull(yearMonth);
    return createFetchItemFromYearMonth(reportType, yearMonth, yearMonth);
  }

  public static FetchItem createFetchItemFromYearMonth(
      String reportType, YearMonth start, YearMonth end) {
    Objects.requireNonNull(reportType);
    Objects.requireNonNull(start);
    Objects.requireNonNull(end);
    return new FetchItem(reportType, start.atDay(1).toString(), end.atEndOfMonth().toString());
  }

  public static List<FetchItem> expand(FetchItem fetchItem) {
    List<YearMonth> months = DateUtil.getYearMonths(fetchItem.getBegin(), fetchItem.getEnd());

    return months.stream()
        .map(
            ym ->
                new FetchItem(
                    fetchItem.getReportType(),
                    ym.atDay(1).toString(),
                    ym.atEndOfMonth().toString()))
        .collect(Collectors.toList());
  }

  public static List<FetchItem> collapse(List<FetchItem> items) {
    Set<Entry<String, List<FetchItem>>> groupedByReportType =
        items.stream()
            .distinct()
            .collect(Collectors.groupingBy(FetchItem::getReportType))
            .entrySet();

    return groupedByReportType.stream()
        .map(
            e ->
                e.getValue().stream()
                    .map(fi -> DateUtil.getYearMonthFromString(fi.getBegin()))
                    .sorted()
                    .reduce(
                        new LinkedHashSet<List<YearMonth>>(),
                        (set, ym) -> {
                          if (set.isEmpty()) {
                            set.add(new ArrayList<>(Collections.singleton(ym)));
                          } else {
                            List<YearMonth> lastList = getLast(set);
                            YearMonth lastMonth = getLast(lastList);
                            if (lastMonth.plusMonths(1).equals(ym) && lastList.size() < MAX_RANGE) {
                              lastList.add(ym);
                            } else {
                              set.add(new ArrayList<>(Collections.singleton(ym)));
                            }
                          }
                          return set;
                        },
                        (s1, s2) -> null)
                    .stream()
                    .map(
                        list ->
                            Optional.ofNullable(getFirst(list, null))
                                .map(ym -> ym.atDay(1).toString())
                                .flatMap(
                                    start ->
                                        Optional.ofNullable(getLast(list, null))
                                            .map(ym -> ym.atEndOfMonth().toString())
                                            .map(end -> new FetchItem(e.getKey(), start, end)))
                                .orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }
}
