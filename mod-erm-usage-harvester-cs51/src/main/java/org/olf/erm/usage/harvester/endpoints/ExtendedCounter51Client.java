package org.olf.erm.usage.harvester.endpoints;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClientOptions;
import org.olf.erm.usage.counter51.client.Counter51Auth;
import org.olf.erm.usage.counter51.client.Counter51Client;

/**
 * Extended Counter 5.1 client with custom error handling.
 *
 * <p>Rate limit errors (429) are wrapped in {@link TooManyRequestsException}. Other HTTP errors are
 * wrapped in {@link ServiceEndpointException} with format: "HTTP {statusCode}: {statusMessage} -
 * {responseBody}". The full body is available via {@link
 * ServiceEndpointException#getResponseBody()}.
 *
 * <p>Parse errors (malformed responses) are wrapped in {@link InvalidReportException}.
 */
public class ExtendedCounter51Client extends Counter51Client {

  public ExtendedCounter51Client(
      Vertx vertx, WebClientOptions options, String baseUrl, Counter51Auth auth) {
    super(vertx, options, baseUrl, auth);
  }

  @Override
  protected <T> Future<T> handleParseError(
      Buffer buffer, int statusCode, Class<T> responseType, Exception parseException) {
    return Future.failedFuture(new InvalidReportException(parseException));
  }

  @Override
  protected <T> Future<T> handleErrorResponse(HttpResponse<Buffer> response) {
    if (response.statusCode() == 429) {
      return Future.failedFuture(new TooManyRequestsException());
    }

    String body = response.body() != null ? response.body().toString() : "";
    return Future.failedFuture(
        new ServiceEndpointException(response.statusCode(), response.statusMessage(), body));
  }
}
