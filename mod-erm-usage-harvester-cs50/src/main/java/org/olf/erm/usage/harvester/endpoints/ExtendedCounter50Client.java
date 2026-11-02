package org.olf.erm.usage.harvester.endpoints;

import static io.vertx.core.Future.failedFuture;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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
      final var body = Optional.ofNullable(buffer).map(Buffer::toString).orElse("");
      // Decode the (possibly large) body only once for all checks below
      final var json = decodeOrNull(body);

      // COUNTER exceptions first, so that e.g. a 1020 isn't hidden by the shape of the body.
      // Otherwise, a SUSHI error or error array (malformed provider response) is kept as before.
      return failedFuture(
          CounterApiException.fromBareBody(statusCode, null, body, json)
              .<Throwable>map(e -> e)
              .orElseGet(
                  () ->
                      isSushiError(json) || json instanceof JsonArray
                          ? new ServiceEndpointException(body)
                          : new InvalidReportException(parseException)));
    }

    // For non-2xx responses, use default behavior
    return super.handleParseError(buffer, statusCode, responseType, parseException);
  }

  /** Same mapper as {@code Json.decodeValue(body, SUSHIErrorModel.class)}, without re-reading. */
  private static boolean isSushiError(Object json) {
    if (!(json instanceof JsonObject o)) {
      return false;
    }
    try {
      o.mapTo(SUSHIErrorModel.class);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static Object decodeOrNull(String body) {
    try {
      return Json.decodeValue(body);
    } catch (DecodeException e) {
      return null;
    }
  }

  @Override
  protected <T> Future<T> handleErrorResponse(HttpResponse<Buffer> response) {
    final var body = Optional.ofNullable(response.body()).map(Buffer::toString).orElse("");
    return failedFuture(
        ServiceEndpoint.createErrorResponseException(
            response.statusCode(), response.statusMessage(), body));
  }
}
