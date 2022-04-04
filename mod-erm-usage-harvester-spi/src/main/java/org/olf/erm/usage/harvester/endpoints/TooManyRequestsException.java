package org.olf.erm.usage.harvester.endpoints;

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
