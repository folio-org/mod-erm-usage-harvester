package org.olf.erm.usage.harvester.endpoints;

import static org.olf.erm.usage.counter51.JsonProperties.EXCEPTIONS;
import static org.olf.erm.usage.counter51.JsonProperties.REPORT_HEADER;

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
import org.olf.erm.usage.counter51.client.Counter51Auth;
import org.olf.erm.usage.counter51.client.Counter51Client;

/**
 * Extended Counter 5.1 client with custom error handling.
 *
 * <p>A response body consisting only of COUNTER exceptions (one object or an array) is wrapped in
 * {@link CounterApiException}, for both success and error status codes.
 *
 * <p>Otherwise, rate limit errors (429) are wrapped in {@link TooManyRequestsException}. Other HTTP
 * errors are wrapped in {@link ServiceEndpointException} with format: "HTTP {statusCode}:
 * {statusMessage} - {responseBody}". The full body is available via {@link
 * ServiceEndpointException#getResponseBody()}.
 *
 * <p>A report that can't be parsed but contains exceptions in its {@code Report_Header} (e.g. a
 * code COUNTER 5.1 doesn't allow there, like 1020) is wrapped in {@link CounterApiException} as
 * well.
 *
 * <p>Other parse errors (malformed responses) are wrapped in {@link InvalidReportException}.
 */
public class ExtendedCounter51Client extends Counter51Client {

  public ExtendedCounter51Client(
      Vertx vertx, WebClientOptions options, String baseUrl, Counter51Auth auth) {
    super(vertx, options, baseUrl, auth);
  }

  @Override
  protected <T> Future<T> handleParseError(
      Buffer buffer, int statusCode, Class<T> responseType, Exception parseException) {
    String body = buffer.toString();
    // Decode the (possibly large) body only once for all checks below
    Object json = decodeOrNull(body);
    return Future.failedFuture(
        CounterApiException.fromBareBody(statusCode, null, body, json)
            .or(() -> reportHeaderExceptions(json).flatMap(CounterApiException::fromReportHeader))
            .<Throwable>map(e -> e)
            .orElseGet(() -> new InvalidReportException(parseException)));
  }

  /**
   * Returns {@code Report_Header.Exceptions} of a report the typed model rejected, e.g. because it
   * contains a 1020, which COUNTER 5.1 doesn't allow in the report header.
   */
  private static Optional<String> reportHeaderExceptions(Object json) {
    if (!(json instanceof JsonObject report)) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(report.getJsonObject(REPORT_HEADER))
          .map(header -> header.getValue(EXCEPTIONS))
          .filter(exceptions -> exceptions instanceof JsonArray array && !array.isEmpty())
          .map(Object::toString);
    } catch (ClassCastException e) {
      return Optional.empty();
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
    String body = response.body() != null ? response.body().toString() : "";
    return Future.failedFuture(
        ServiceEndpoint.createErrorResponseException(
            response.statusCode(), response.statusMessage(), body));
  }
}
