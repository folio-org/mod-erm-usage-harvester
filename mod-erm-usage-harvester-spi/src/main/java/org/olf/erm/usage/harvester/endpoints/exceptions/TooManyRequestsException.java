package org.olf.erm.usage.harvester.endpoints.exceptions;

import org.folio.rest.jaxrs.model.ApiException;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;

/**
 * Exception thrown if {@link ServiceEndpoint#fetchReport(String, String, String)} received an error
 * indicating rate-limiting or throttling from the provider (HTTP 429).
 */
public class TooManyRequestsException extends RuntimeException implements ApiExceptionProvider {

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

  @Override
  public ApiException toApiException() {
    return new ApiException()
        .withCode(TOO_MANY_REQUEST_ERROR_CODE)
        .withMessage(TOO_MANY_REQUEST_STR);
  }
}
