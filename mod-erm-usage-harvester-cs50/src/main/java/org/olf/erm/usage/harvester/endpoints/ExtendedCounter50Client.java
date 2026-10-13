package org.olf.erm.usage.harvester.endpoints;

import static io.vertx.core.Future.failedFuture;
import static org.olf.erm.usage.harvester.endpoints.JsonUtil.isJsonArray;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.Optional;
import org.olf.erm.usage.counter50.client.Counter50Auth;
import org.olf.erm.usage.counter50.client.Counter50Client;
import org.openapitools.counter50.model.SUSHIErrorModel;

/**
 * Extended Counter 5.0 client that validates responses and handles malformed provider behavior.
 *
 * <p>A response body consisting only of COUNTER exceptions (one object or an array) is wrapped in
 * {@link CounterApiException}, for both success and error status codes.
 *
 * <p>Otherwise, rate limit errors (429) are wrapped in {@link TooManyRequestsException}. Other HTTP
 * errors are wrapped in {@link ServiceEndpointException} with format: "HTTP {statusCode}:
 * {statusMessage} - {responseBody}". The full body is available via {@link
 * ServiceEndpointException#getResponseBody()}.
 *
 * <p>Some providers incorrectly return SUSHI error responses with 2xx status codes instead of
 * proper 4xx/5xx codes. If such a body isn't a list of COUNTER exceptions, but still looks like a
 * SUSHI error or a JSON array, it is wrapped in {@link ServiceEndpointException}.
 *
 * <p>Parse errors (malformed responses) are wrapped in {@link InvalidReportException}.
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

      // COUNTER exceptions delivered with a success status code
      Optional<CounterApiException> counterApiException =
          CounterApiException.fromBareBody(statusCode, null, body);
      if (counterApiException.isPresent()) {
        return failedFuture(counterApiException.get());
      }

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
    int statusCode = response.statusCode();
    String body = response.body() != null ? response.body().toString() : "";
    return failedFuture(
        CounterApiException.fromBareBody(statusCode, response.statusMessage(), body)
            .<Throwable>map(e -> e)
            .orElseGet(
                () ->
                    statusCode == 429
                        ? new TooManyRequestsException()
                        : new ServiceEndpointException(
                            statusCode, response.statusMessage(), body)));
  }
}
