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
        .toList();
  }

  /**
   * Collapses a list of {@link FetchItem} objects based on their report type and date ranges.
   *
   * <p>This method takes a list of {@link FetchItem} objects and groups them by their report type.
   * For report type "TR" the items remain unchanged. For other report types, the items are grouped
   * into date ranges where each range spans consecutive months and contains up to a maximum number
   * of months specified by {@code MAX_RANGE}.
   *
   * @param items The list of {@link FetchItem} objects to be collapsed.
   * @return A collapsed list of {@link FetchItem} objects, where items with report type "TR" remain
   *     unchanged, and items with other report types are grouped into date ranges. The resulting
   *     list is sorted by report type and date.
   * @see FetchItem
   */
  public static List<FetchItem> collapse(List<FetchItem> items) {
    Set<Entry<String, List<FetchItem>>> groupedByReportType =
        items.stream()
            .distinct()
            .collect(Collectors.groupingBy(FetchItem::getReportType))
            .entrySet();

    return groupedByReportType.stream()
        .map(
            e -> {
              if ("TR".equals(e.getKey())) {
                return e.getValue();
              } else {
                return e.getValue().stream()
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
                    .toList();
              }
            })
        .flatMap(Collection::stream)
        .toList();
  }
}
