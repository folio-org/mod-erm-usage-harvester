package org.olf.erm.usage.harvester;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Objects.requireNonNull;
import static org.olf.erm.usage.harvester.Messages.MSG_RESPONSE_BODY_IS_NULL;
import static org.olf.erm.usage.harvester.Messages.createMsgStatus;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

public class HttpResponseUtil {

  public static <T> Future<T> getResponseBodyIfStatus200(
      AsyncResult<HttpResponse<Buffer>> ar, Class<T> entityClass) {
    if (ar.succeeded()) {
      if (ar.result().statusCode() == 200) {
        return succeededFuture(
            requireNonNull(ar.result().bodyAsJson(entityClass), MSG_RESPONSE_BODY_IS_NULL));
      } else {
        return failedFuture(createMsgStatus(ar.result().statusCode(), ar.result().statusMessage()));
      }
    } else {
      return failedFuture(ar.cause().getMessage());
    }
  }

  private HttpResponseUtil() {}
}
