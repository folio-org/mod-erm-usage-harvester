package org.olf.erm.usage.harvester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Test {@link FetchItem} */
class FetchItemTest {

  @Test
  void testCreateFetchItemFromYearMonth() {
    FetchItem fi1 = FetchItem.of("JR1", YearMonth.of(2000, 1));
    assertThat(fi1)
        .satisfies(
            fi -> {
              assertThat(fi.reportType()).isEqualTo("JR1");
              assertThat(fi.begin()).isEqualTo("2000-01-01");
              assertThat(fi.end()).isEqualTo("2000-01-31");
            });

    FetchItem fi2 = FetchItem.of("TR1", YearMonth.of(2020, 2));
    assertThat(fi2)
        .satisfies(
            fi -> {
              assertThat(fi.reportType()).isEqualTo("TR1");
              assertThat(fi.begin()).isEqualTo("2020-02-01");
              assertThat(fi.end()).isEqualTo("2020-02-29");
            });

    FetchItem fi3 = FetchItem.of("PR1", YearMonth.of(2020, 1), YearMonth.of(2020, 2));
    assertThat(fi3)
        .satisfies(
            fi -> {
              assertThat(fi.reportType()).isEqualTo("PR1");
              assertThat(fi.begin()).isEqualTo("2020-01-01");
              assertThat(fi.end()).isEqualTo("2020-02-29");
            });

    assertThatNullPointerException().isThrownBy(() -> FetchItem.of(null, YearMonth.of(2020, 1)));
    YearMonth yearMonth = null;
    assertThatNullPointerException().isThrownBy(() -> FetchItem.of("JR1", yearMonth));
    assertThatNullPointerException().isThrownBy(() -> FetchItem.of("JR1", YearMonth.now(), null));
    assertThatNullPointerException().isThrownBy(() -> FetchItem.of("JR1", null, YearMonth.now()));
  }

  @Test
  void testCreateFetchItemFromYearMonthList() {
    FetchItem fi1 =
        FetchItem.of(
            "JR1", List.of(YearMonth.of(2020, 1), YearMonth.of(2020, 2), YearMonth.of(2020, 3)));
    assertThat(fi1)
        .satisfies(
            fi -> {
              assertThat(fi.reportType()).isEqualTo("JR1");
              assertThat(fi.begin()).isEqualTo("2020-01-01");
              assertThat(fi.end()).isEqualTo("2020-03-31");
            });

    FetchItem fi2 = FetchItem.of("TR1", List.of(YearMonth.of(2020, 2)));
    assertThat(fi2)
        .satisfies(
            fi -> {
              assertThat(fi.reportType()).isEqualTo("TR1");
              assertThat(fi.begin()).isEqualTo("2020-02-01");
              assertThat(fi.end()).isEqualTo("2020-02-29");
            });

    assertThatNullPointerException()
        .isThrownBy(() -> FetchItem.of(null, List.of(YearMonth.of(2020, 1))));
    List<YearMonth> nullList = null;
    assertThatNullPointerException().isThrownBy(() -> FetchItem.of("JR1", nullList));
  }

  @Test
  void testCompactConstructorNullChecks() {
    assertThatNullPointerException()
        .isThrownBy(() -> new FetchItem(null, "2020-01-01", "2020-01-31"))
        .withMessage("reportType cannot be null");
    assertThatNullPointerException()
        .isThrownBy(() -> new FetchItem("JR1", null, "2020-01-31"))
        .withMessage("begin cannot be null");
    assertThatNullPointerException()
        .isThrownBy(() -> new FetchItem("JR1", "2020-01-01", null))
        .withMessage("end cannot be null");
  }

  @Test
  void testExpandJR1() {
    final String reportType = "JR1";

    FetchItem fetchItem = new FetchItem(reportType, "2019-12-01", "2020-02-29");
    List<FetchItem> expand = fetchItem.expand();

    assertThat(expand)
        .containsExactlyInAnyOrder(
            FetchItem.of(reportType, YearMonth.of(2019, 12)),
            FetchItem.of(reportType, YearMonth.of(2020, 1)),
            FetchItem.of(reportType, YearMonth.of(2020, 2)));
  }
}
