package org.olf.erm.usage.harvester.endpoints;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingConstants.MAX_ERROR_BODY_LENGTH;

/**
 * Exception for errors from ServiceEndpoint implementations. Stores HTTP status codes and response
 * bodies.
 */
public class ServiceEndpointException extends RuntimeException {

  private final Integer statusCode;
  private final String responseBody;

  /**
   * Private constructor that initializes the exception with a message, status code, and response
   * body.
   *
   * @param message The exception message
   * @param statusCode The HTTP status code, or null if not applicable
   * @param responseBody The response body
   */
  private ServiceEndpointException(String message, Integer statusCode, String responseBody) {
    super(message);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }

  /**
   * Constructs an exception for HTTP errors with a formatted message. The message format is "HTTP
   * {statusCode}: {statusMessage} - {abbreviated response body}". The response body in the message
   * is abbreviated to {@link ErrorHandlingConstants#MAX_ERROR_BODY_LENGTH} characters.
   *
   * @param statusCode The HTTP status code
   * @param statusMessage The HTTP status message
   * @param responseBody The response body, can be null or empty
   */
  public ServiceEndpointException(int statusCode, String statusMessage, String responseBody) {
    this(
        createHttpErrorMessage(statusCode, statusMessage, responseBody),
        statusCode,
        responseBody == null || responseBody.isEmpty() ? "[no body]" : responseBody);
  }

  /**
   * Creates a formatted error message for HTTP errors.
   *
   * @param statusCode The HTTP status code
   * @param statusMessage The HTTP status message
   * @param responseBody The response body, can be null or empty
   * @return A formatted error message
   */
  private static String createHttpErrorMessage(
      int statusCode, String statusMessage, String responseBody) {
    String body = (responseBody == null || responseBody.isEmpty()) ? "[no body]" : responseBody;
    String bodyAbbr = abbreviate(body, MAX_ERROR_BODY_LENGTH);
    return "HTTP " + statusCode + ": " + statusMessage + " - " + bodyAbbr;
  }

  /**
   * Constructs an exception with the response body as the message. The message is abbreviated to
   * {@link ErrorHandlingConstants#MAX_ERROR_BODY_LENGTH} characters.
   *
   * @param responseBody The response body
   */
  public ServiceEndpointException(String responseBody) {
    this(abbreviate(responseBody, MAX_ERROR_BODY_LENGTH), null, responseBody);
  }

  /**
   * Returns the HTTP status code, or null if not applicable.
   *
   * @return The HTTP status code or null
   */
  public Integer getStatusCode() {
    return statusCode;
  }

  /**
   * Returns the full (non-abbreviated) response body.
   *
   * @return The complete response body
   */
  public String getResponseBody() {
    return responseBody;
  }
}
