package org.olf.erm.usage.harvester.endpoints;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.olf.erm.usage.harvester.endpoints.ErrorHandlingConstants.MAX_ERROR_BODY_LENGTH;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClientOptions;
import org.olf.erm.usage.counter51.client.Counter51Auth;
import org.olf.erm.usage.counter51.client.Counter51Client;
import org.olf.erm.usage.counter51.client.Counter51ClientException;

/**
 * Extended Counter 5.1 client that detects 429 (Too Many Requests) errors at the response level and
 * includes the response body in error messages.
 *
 * <p>Error message format: "HTTP {statusCode}: {statusMessage} - {responseBody}"
 *
 * <p>Response bodies longer than 2000 characters are abbreviated in the message with a "..."
 * suffix. The full body is still available via Counter51ClientException.getResponseBody().
 */
public class ExtendedCounter51Client extends Counter51Client {

  public ExtendedCounter51Client(
      Vertx vertx, WebClientOptions options, String baseUrl, Counter51Auth auth) {
    super(vertx, options, baseUrl, auth);
  }

  @Override
  protected <T> Future<T> handleErrorResponse(HttpResponse<Buffer> response) {
    if (response.statusCode() == 429) {
      return Future.failedFuture(new TooManyRequestsException());
    }

    // Format message with abbreviated response body: "HTTP 404: Not Found - {body}"
    String body = response.body() != null ? response.body().toString() : "";
    if (body.isEmpty()) {
      body = "[no body]";
    }

    // Abbreviate body for the message (full body still stored in exception.responseBody)
    String bodyAbbr = abbreviate(body, MAX_ERROR_BODY_LENGTH);

    String message =
        "HTTP " + response.statusCode() + ": " + response.statusMessage() + " - " + bodyAbbr;

    return Future.failedFuture(new Counter51ClientException(message, response.statusCode(), body));
  }
}
