package org.olf.erm.usage.harvester.endpoints;

public record FetchItem(String reportType, String begin, String end) {

  public String getReportType() {
    return reportType;
  }

  public String getBegin() {
    return begin;
  }

  public String getEnd() {
    return end;
  }
}
