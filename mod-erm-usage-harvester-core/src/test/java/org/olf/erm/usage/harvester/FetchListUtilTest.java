package org.olf.erm.usage.harvester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.olf.erm.usage.harvester.FetchListUtil.collapse;
import static org.olf.erm.usage.harvester.FetchListUtil.createFetchItemFromYearMonth;
import static org.olf.erm.usage.harvester.FetchListUtil.expand;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Test;

public class FetchListUtilTest {

  private static List<FetchItem> createSampleFetchList() {
    List<YearMonth> months1 =
        IntStream.range(0, 40).boxed().map(YearMonth.of(2018, 1)::plusMonths).toList();
    List<YearMonth> months2 = Arrays.asList(YearMonth.of(2022, 1), YearMonth.of(2022, 2));
    List<YearMonth> months3 =
        IntStream.range(0, 14).boxed().map(YearMonth.of(2018, 7)::plusMonths).toList();

    List<FetchItem> jr1 =
        Stream.concat(months1.stream(), months2.stream())
            .map(ym -> createFetchItemFromYearMonth("JR1", ym))
            .toList();

    List<FetchItem> pr1 =
        months3.stream().map(ym -> createFetchItemFromYearMonth("PR1", ym)).toList();

    List<FetchItem> tr =
        months2.stream().map(ym -> createFetchItemFromYearMonth("TR", ym)).toList();

    List<FetchItem> duplicates =
        List.of(
            createFetchItemFromYearMonth("JR1", YearMonth.of(2018, 1)),
            createFetchItemFromYearMonth("PR1", YearMonth.of(2018, 7)));

    return Stream.of(jr1, pr1, tr, duplicates).flatMap(Collection::stream).toList();
  }

  @Test
  public void testCreateFetchItemFromYearMonth() {
    FetchItem fi1 = createFetchItemFromYearMonth("JR1", YearMonth.of(2000, 1));
    assertThat(fi1)
        .satisfies(
            fi -> {
              assertThat(fi.getReportType()).isEqualTo("JR1");
              assertThat(fi.getBegin()).isEqualTo("2000-01-01");
              assertThat(fi.getEnd()).isEqualTo("2000-01-31");
            });

    FetchItem fi2 = createFetchItemFromYearMonth("TR1", YearMonth.of(2020, 2));
    assertThat(fi2)
        .satisfies(
            fi -> {
              assertThat(fi.getReportType()).isEqualTo("TR1");
              assertThat(fi.getBegin()).isEqualTo("2020-02-01");
              assertThat(fi.getEnd()).isEqualTo("2020-02-29");
            });

    FetchItem fi3 =
        createFetchItemFromYearMonth("PR1", YearMonth.of(2020, 1), YearMonth.of(2020, 2));
    assertThat(fi3)
        .satisfies(
            fi -> {
              assertThat(fi.getReportType()).isEqualTo("PR1");
              assertThat(fi.getBegin()).isEqualTo("2020-01-01");
              assertThat(fi.getEnd()).isEqualTo("2020-02-29");
            });

    assertThatNullPointerException()
        .isThrownBy(() -> createFetchItemFromYearMonth(null, YearMonth.of(2020, 1)));
    assertThatNullPointerException().isThrownBy(() -> createFetchItemFromYearMonth("JR1", null));
    assertThatNullPointerException()
        .isThrownBy(() -> createFetchItemFromYearMonth("JR1", YearMonth.now(), null));
    assertThatNullPointerException()
        .isThrownBy(() -> createFetchItemFromYearMonth("JR1", null, YearMonth.now()));
  }

  @Test
  public void testCollapseJR1() {
    final String reportType = "JR1";
    List<FetchItem> fetchItemList =
        List.of(
            createFetchItemFromYearMonth(reportType, YearMonth.of(2019, 12)),
            createFetchItemFromYearMonth(reportType, YearMonth.of(2020, 1)),
            createFetchItemFromYearMonth(reportType, YearMonth.of(2020, 2)));
    List<FetchItem> result = collapse(fetchItemList);

    assertThat(fetchItemList).hasSize(3);
    assertThat(result)
        .hasSize(1)
        .containsExactly(
            createFetchItemFromYearMonth(
                reportType, YearMonth.of(2019, 12), YearMonth.of(2020, 2)));
  }

  @Test
  public void testCollapseTR() {
    final String reportType = "TR";
    List<FetchItem> fetchItemList =
        List.of(
            createFetchItemFromYearMonth(reportType, YearMonth.of(2019, 12)),
            createFetchItemFromYearMonth(reportType, YearMonth.of(2020, 1)),
            createFetchItemFromYearMonth(reportType, YearMonth.of(2020, 2)));
    List<FetchItem> result = collapse(fetchItemList);

    assertThat(fetchItemList).hasSize(3);
    assertThat(result).hasSize(3).containsExactlyElementsOf(fetchItemList);
  }

  @Test
  public void testCollapseMultipleReportTypes() {
    List<FetchItem> collapsed = collapse(createSampleFetchList());
    assertThat(collapsed)
        .containsExactlyInAnyOrder(
            createFetchItemFromYearMonth("JR1", YearMonth.of(2018, 1), YearMonth.of(2018, 12)),
            createFetchItemFromYearMonth("JR1", YearMonth.of(2019, 1), YearMonth.of(2019, 12)),
            createFetchItemFromYearMonth("JR1", YearMonth.of(2020, 1), YearMonth.of(2020, 12)),
            createFetchItemFromYearMonth("JR1", YearMonth.of(2021, 1), YearMonth.of(2021, 4)),
            createFetchItemFromYearMonth("JR1", YearMonth.of(2022, 1), YearMonth.of(2022, 2)),
            createFetchItemFromYearMonth("PR1", YearMonth.of(2018, 7), YearMonth.of(2019, 6)),
            createFetchItemFromYearMonth("PR1", YearMonth.of(2019, 7), YearMonth.of(2019, 8)),
            createFetchItemFromYearMonth("TR", YearMonth.of(2022, 1)),
            createFetchItemFromYearMonth("TR", YearMonth.of(2022, 2)));
  }

  @Test
  public void testCollapseAndExpandMultipleReportTypes() {
    List<FetchItem> distinctFetchList =
        createSampleFetchList().stream().distinct().collect(Collectors.toList());
    List<FetchItem> collapsed = collapse(distinctFetchList);
    List<FetchItem> expanded =
        collapsed.stream().flatMap(fi -> expand(fi).stream()).collect(Collectors.toList());
    assertThat(distinctFetchList).containsExactlyInAnyOrderElementsOf(expanded);
  }

  @Test
  public void testExpandJR1() {
    final String reportType = "JR1";

    FetchItem fetchItem = new FetchItem(reportType, "2019-12-01", "2020-02-29");
    List<FetchItem> expand = FetchListUtil.expand(fetchItem);

    assertThat(expand)
        .containsExactlyInAnyOrder(
            createFetchItemFromYearMonth(reportType, YearMonth.of(2019, 12)),
            createFetchItemFromYearMonth(reportType, YearMonth.of(2020, 1)),
            createFetchItemFromYearMonth(reportType, YearMonth.of(2020, 2)));
  }
}
