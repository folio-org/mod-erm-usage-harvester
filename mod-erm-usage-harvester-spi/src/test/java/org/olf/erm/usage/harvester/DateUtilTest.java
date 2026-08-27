package org.olf.erm.usage.harvester;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public class DateUtilTest {
  @Test
  public void testGetYearMonths() {
    List<YearMonth> result1 = DateUtil.getYearMonths("2018-01", "2019-06");
    assertThat(result1)
        .hasSize(18)
        .containsExactlyElementsOf(
            Stream.iterate(YearMonth.of(2018, 1), ym -> ym.plusMonths(1))
                .limit(18)
                .collect(Collectors.toList()));

    List<YearMonth> result2 = DateUtil.getYearMonths("2018-01-01", "2019-06-30");
    List<YearMonth> result3 = DateUtil.getYearMonths("2018-01-05", "2019-06-15");
    assertThat(result1).isEqualTo(result2).isEqualTo(result3);

    List<YearMonth> result4 = DateUtil.getYearMonths("2020-01", "2018-12");
    assertThat(result4).isEmpty();

    List<YearMonth> result5 = DateUtil.getYearMonths("2018-01", "2018-01");
    assertThat(result5).containsExactly(YearMonth.of(2018, 1));
  }

  @Test
  public void testGetYearMonthFromStringWithLimit() {
    YearMonth r1 = DateUtil.getYearMonthFromStringWithLimit("2020-01-01", YearMonth.of(2019, 12));
    YearMonth r2 = DateUtil.getYearMonthFromStringWithLimit("2020-01-15", YearMonth.of(2019, 12));
    YearMonth r3 = DateUtil.getYearMonthFromStringWithLimit("", YearMonth.of(2019, 12));
    YearMonth r4 = DateUtil.getYearMonthFromStringWithLimit(null, YearMonth.of(2019, 12));
    assertThat(r1).isEqualTo(YearMonth.of(2019, 12)).isEqualTo(r2).isEqualTo(r3).isEqualTo(r4);

    YearMonth r5 = DateUtil.getYearMonthFromStringWithLimit("2018-01-01", YearMonth.of(2019, 12));
    YearMonth r6 = DateUtil.getYearMonthFromStringWithLimit("2018-01-15", YearMonth.of(2019, 12));
    YearMonth r7 = DateUtil.getYearMonthFromStringWithLimit("2018-01-15", YearMonth.of(2018, 1));
    assertThat(r5).isEqualTo(YearMonth.of(2018, 1)).isEqualTo(r6).isEqualTo(r7);
  }

  @Test(expected = DateTimeParseException.class)
  public void testGetYearMonthsInvalidFormat() {
    DateUtil.getYearMonths("01.01.2018", "31.01.2018");
  }

  @Test(expected = NullPointerException.class)
  public void testGetYearMonthsStartStrNull() {
    DateUtil.getYearMonths(null, "2080-12");
  }

  @Test(expected = DateTimeParseException.class)
  public void testGetYearMonthsStartStrEmpty() {
    DateUtil.getYearMonths("", "2080-12");
  }

  @Test(expected = NullPointerException.class)
  public void testGetYearMonthsEndStrNull() {
    DateUtil.getYearMonths("2020-01", null);
  }

  @Test(expected = DateTimeParseException.class)
  public void testGetYearMonthsEndStrEmpty() {
    DateUtil.getYearMonths("2020-01", "");
  }

  @Test(expected = NullPointerException.class)
  public void testGetYearMonthsStartMonthIsNull() {
    DateUtil.getYearMonths(null, YearMonth.now());
  }

  @Test(expected = NullPointerException.class)
  public void testGetYearMonthsEndMonthIsNull() {
    DateUtil.getYearMonths(YearMonth.now(), null);
  }
}
