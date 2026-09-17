package org.olf.erm.usage.harvester.endpoints.exceptions;

import org.folio.rest.jaxrs.model.ApiException;
import org.olf.erm.usage.harvester.endpoints.ServiceEndpoint;

/**
 * Default exception thrown if {@link ServiceEndpoint#fetchReport(String, String, String)} retrieved
 * a report that contains COUNTER Exceptions or invalid data.
 */
public class InvalidReportException extends RuntimeException implements ApiExceptionProvider {

  public InvalidReportException(Throwable cause) {
    super(cause);
  }

  public InvalidReportException(String message) {
    super("Report not valid: " + message);
  }

  @Override
  public ApiException toApiException() {
    return new ApiException().withCode(1).withMessage(getMessage());
  }
}
