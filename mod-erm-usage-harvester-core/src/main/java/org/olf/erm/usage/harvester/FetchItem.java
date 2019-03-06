package org.olf.erm.usage.harvester;

import java.util.Objects;

public class FetchItem {
  String reportType;
  String begin;
  String end;

  public FetchItem(String reportType, String begin, String end) {
    super();
    this.reportType = reportType;
    this.begin = begin;
    this.end = end;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FetchItem fetchItem = (FetchItem) o;
    return Objects.equals(reportType, fetchItem.reportType)
        && Objects.equals(begin, fetchItem.begin)
        && Objects.equals(end, fetchItem.end);
  }

  @Override
  public int hashCode() {
    return Objects.hash(reportType, begin, end);
  }

  @Override
  public String toString() {
    return "FetchItem [reportType=" + reportType + ", begin=" + begin + ", end=" + end + "]";
  }
}
