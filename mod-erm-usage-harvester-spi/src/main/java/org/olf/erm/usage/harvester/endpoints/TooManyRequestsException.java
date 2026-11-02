package org.olf.erm.usage.harvester.endpoints;

/**
 * Exception thrown if {@link ServiceEndpoint#fetchReport(String, String, String)} received an error
 * indicating rate-limiting or throttling from the provider (HTTP 429).
 */
public class TooManyRequestsException extends RuntimeException {

  public static final String TOO_MANY_REQUEST_STR = "too many requests";
  public static final int TOO_MANY_REQUEST_ERROR_CODE = 1020;

  public TooManyRequestsException() {
    super();
  }

  public TooManyRequestsException(String message) {
    super(message);
  }

  public TooManyRequestsException(String message, Throwable cause) {
    super(message, cause);
  }
}
