package org.olf.erm.usage.harvester.endpoints;

import static io.vertx.core.Future.failedFuture;
import static org.olf.erm.usage.harvester.endpoints.JsonUtil.isJsonArray;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.olf.erm.usage.counter50.client.Counter50Auth;
import org.olf.erm.usage.counter50.client.Counter50Client;
import org.openapitools.counter50.model.SUSHIErrorModel;

/**
 * Extended Counter 5.0 client that validates responses and handles malformed provider behavior.
 *
 * <p>Some providers incorrectly return SUSHI error responses with 2xx status codes instead of
 * proper 4xx/5xx codes. This client detects these cases and wraps them in {@link
 * ServiceEndpointException}.
 *
 * <p>Rate limit errors (429) are wrapped in {@link TooManyRequestsException}. Other HTTP errors are
 * wrapped in {@link ServiceEndpointException} with format: "HTTP {statusCode}: {statusMessage} -
 * {responseBody}". The full body is available via {@link
 * ServiceEndpointException#getResponseBody()}.
 *
 * <p>Parse errors (malformed responses) are wrapped in {@link InvalidReportException}.
 */
public class ExtendedCounter50Client extends Counter50Client {

  public ExtendedCounter50Client(WebClient client, String baseUrl, Counter50Auth auth) {
    super(client, baseUrl, auth);
  }

  @Override
  protected <T> Future<T> handleParseError(
      Buffer buffer, int statusCode, Class<T> responseType, Exception parseException) {

    // Only apply special handling for 2xx responses that fail to parse
    // This catches providers that return SUSHI errors with success status codes
    if (statusCode >= 200 && statusCode < 300) {
      String body = buffer.toString();

      // Check if it's actually a SUSHI error or error array (malformed provider response)
      if (JsonUtil.isOfType(body, SUSHIErrorModel.class) || isJsonArray(body)) {
        return failedFuture(new ServiceEndpointException(body));
      } else {
        return failedFuture(new InvalidReportException(parseException));
      }
    }

    // For non-2xx responses, use default behavior
    return super.handleParseError(buffer, statusCode, responseType, parseException);
  }

  @Override
  protected <T> Future<T> handleErrorResponse(HttpResponse<Buffer> response) {
    if (response.statusCode() == 429) {
      return failedFuture(new TooManyRequestsException());
    }

    String body = response.body() != null ? response.body().toString() : "";
    return failedFuture(
        new ServiceEndpointException(response.statusCode(), response.statusMessage(), body));
  }
}
