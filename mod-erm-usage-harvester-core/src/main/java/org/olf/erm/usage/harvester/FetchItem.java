package org.olf.erm.usage.harvester;

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
  public String toString() {
    return "FetchItem [reportType=" + reportType + ", begin=" + begin + ", end=" + end + "]";
  }

}
