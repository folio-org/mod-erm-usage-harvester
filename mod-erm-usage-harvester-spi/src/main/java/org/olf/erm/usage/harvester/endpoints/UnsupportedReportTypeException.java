package org.olf.erm.usage.harvester.endpoints;

public class UnsupportedReportTypeException extends RuntimeException {

  public static final String MSG_UNSUPPORTED_REPORT_TYPE = "Unsupported Report Type: %s";

  public UnsupportedReportTypeException(String reportType) {
    super(String.format(MSG_UNSUPPORTED_REPORT_TYPE, reportType));
  }
}
