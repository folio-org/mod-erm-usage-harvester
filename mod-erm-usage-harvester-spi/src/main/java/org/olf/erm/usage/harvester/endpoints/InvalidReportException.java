package org.olf.erm.usage.harvester.endpoints;

/**
 * Default exception thrown if {@link ServiceEndpoint#fetchReport(String, String, String)} retrieved
 * a report that contains COUNTER Exceptions or invalid data.
 */
public class InvalidReportException extends RuntimeException {

  public InvalidReportException(Throwable cause) {
    super(cause);
  }

  public InvalidReportException(String message) {
    super("Report not valid: " + message);
  }
}
