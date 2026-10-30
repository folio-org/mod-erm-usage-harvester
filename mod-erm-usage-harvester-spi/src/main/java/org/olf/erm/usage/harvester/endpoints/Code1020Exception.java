package org.olf.erm.usage.harvester.endpoints;

/**
 * Exception thrown if {@link ServiceEndpoint#fetchReport(String, String, String)} received an error
 * indicating rate-limiting or throttling from the provider (HTTP 429).
 */
public class Code1020Exception extends RuntimeException {

  public static final String ERROR_CODE_1020_STR = "too many requests";
  public static final int ERROR_CODE_1020 = 1020;

  public Code1020Exception() {
    super();
  }

  public Code1020Exception(String message) {
    super(message);
  }

  public Code1020Exception(String message, Throwable cause) {
    super(message, cause);
  }
}
