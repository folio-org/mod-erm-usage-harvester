package org.olf.erm.usage.harvester.endpoints;

public class InvalidReportException extends RuntimeException {

  public InvalidReportException(Throwable cause) {
    super(cause);
  }

  public InvalidReportException(String message) {
    super("Report not valid: " + message);
  }
}
