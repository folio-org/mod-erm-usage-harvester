package org.olf.erm.usage.harvester.endpoints;

/**
 * Exception thrown if that the specific implementation of {@link ServiceEndpoint} does not support
 * the requested report type.
 */
public class UnsupportedReportTypeException extends RuntimeException {

  public static final String MSG_UNSUPPORTED_REPORT_TYPE = "Unsupported Report Type: %s";

  public UnsupportedReportTypeException(String reportType) {
    super(String.format(MSG_UNSUPPORTED_REPORT_TYPE, reportType));
  }
}
