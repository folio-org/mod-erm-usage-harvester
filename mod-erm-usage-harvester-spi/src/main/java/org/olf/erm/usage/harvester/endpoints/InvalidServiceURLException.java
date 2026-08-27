package org.olf.erm.usage.harvester.endpoints;

/**
 * Exception thrown if {@link ServiceEndpoint#fetchReport(String, String, String)} received an
 * invalid service URL to consume.
 */
public class InvalidServiceURLException extends RuntimeException {

  public static final String MSG_INVALID_SERVICE_URL = "Invalid Service URL: %s";

  public InvalidServiceURLException(String url) {
    super(String.format(MSG_INVALID_SERVICE_URL, url));
  }
}
