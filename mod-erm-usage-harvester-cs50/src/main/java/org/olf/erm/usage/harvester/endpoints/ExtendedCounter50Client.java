package org.olf.erm.usage.harvester.endpoints;

import static io.vertx.core.Future.failedFuture;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingConstants.MAX_ERROR_BODY_LENGTH;
import static org.olf.erm.usage.harvester.endpoints.JsonUtil.isJsonArray;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClientOptions;
import org.olf.erm.usage.counter50.client.Counter50Auth;
import org.olf.erm.usage.counter50.client.Counter50Client;
import org.openapitools.counter50.model.SUSHIErrorModel;

/**
 * Extended Counter 5.0 client that validates responses and handles malformed provider behavior.
 *
 * <p>Some providers incorrectly return SUSHI error responses with 2xx status codes instead of
 * proper 4xx/5xx codes. This client detects these cases and properly categorizes them as errors.
 */
public class ExtendedCounter50Client extends Counter50Client {

  public ExtendedCounter50Client(
      Vertx vertx, WebClientOptions options, String baseUrl, Counter50Auth auth) {
    super(vertx, options, baseUrl, auth);
  }

  @Override
  protected <T> Future<T> handleParseError(
      Buffer buffer, int statusCode, Class<T> responseType, Exception parseException) {

    // Only apply special handling for 2xx responses that fail to parse
    // This catches providers that return SUSHI errors with success status codes
    if (statusCode >= 200 && statusCode < 300) {
      String body = buffer.toString();
      String bodyAbbr = abbreviate(body, MAX_ERROR_BODY_LENGTH);

      // Check if it's actually a SUSHI error or error array (malformed provider response)
      if (JsonUtil.isOfType(body, SUSHIErrorModel.class) || isJsonArray(body)) {
        // Return raw error body as string (not wrapped in exception)
        return failedFuture(bodyAbbr);
      } else {
        return failedFuture(new InvalidReportException(parseException));
      }
    }

    // For non-2xx responses, use default behavior
    return super.handleParseError(buffer, statusCode, responseType, parseException);
  }

  @Override
  protected <T> Future<T> handleErrorResponse(HttpResponse<Buffer> response) {
    // Add special handling for 429 Too Many Requests errors
    if (response.statusCode() == 429) {
      return failedFuture(new TooManyRequestsException());
    }

    // For other error responses, return raw body as string (not wrapped in exception)
    String respBodyAbbr = response.body() != null ? response.body().toString() : "";
    if (respBodyAbbr.isEmpty()) {
      respBodyAbbr = response.statusCode() + " - " + response.statusMessage();
    }
    respBodyAbbr = abbreviate(respBodyAbbr, MAX_ERROR_BODY_LENGTH);
    return failedFuture(respBodyAbbr);
  }
}
