package org.olf.erm.usage.harvester;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.Test;

public class DateUtilTest {
  @Test
  public void testValues() {
    YearMonth current = YearMonth.now();

    List<YearMonth> result1 = DateUtil.getYearMonths("2018-01", "2080-12");
    assertEquals(YearMonth.parse("2018-01").until(current, ChronoUnit.MONTHS), result1.size());

    List<YearMonth> result2 = DateUtil.getYearMonths("2018-01", "");
    assertEquals(YearMonth.parse("2018-01").until(current, ChronoUnit.MONTHS), result2.size());

    List<YearMonth> result3 = DateUtil.getYearMonths("2018-01", null);
    assertEquals(YearMonth.parse("2018-01").until(current, ChronoUnit.MONTHS), result3.size());

    List<YearMonth> result4 = DateUtil.getYearMonths(YearMonth.now().toString(), null);
    assertTrue(result4.isEmpty());

    List<YearMonth> result5 =
        DateUtil.getYearMonths(YearMonth.now().plusMonths(3).toString(), "2080-12");
    assertTrue(result5.isEmpty());

    List<YearMonth> result6 = DateUtil.getYearMonths("2018-01", "2018-01");
    assertEquals(1, result6.size());

    List<YearMonth> result7 = DateUtil.getYearMonths("2018-01", "2018-02");
    assertEquals(2, result7.size());
    assertEquals("2018-01", result7.get(0).toString());
    assertEquals("2018-02", result7.get(1).toString());
  }

  @Test(expected = NullPointerException.class)
  public void testStartStrNull() {
    DateUtil.getYearMonths(null, "2080-12");
  }

  @Test(expected = NullPointerException.class)
  public void testStartStrEmpty() {
    DateUtil.getYearMonths("", "2080-12");
  }
}
