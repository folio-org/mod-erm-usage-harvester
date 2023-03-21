package org.openapitools.client;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.olf.erm.usage.harvester.endpoints.CS50Impl.MAX_ERROR_BODY_LENGTH;
import static org.olf.erm.usage.harvester.endpoints.JsonUtil.isJsonArray;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.client.HttpResponse;
import org.apache.commons.lang3.StringUtils;
import org.olf.erm.usage.harvester.endpoints.InvalidReportException;
import org.olf.erm.usage.harvester.endpoints.JsonUtil;
import org.olf.erm.usage.harvester.endpoints.TooManyRequestsException;
import org.openapitools.client.model.SUSHIErrorModel;

public class CounterApiClient extends ApiClient {

  public CounterApiClient(Vertx vertx, JsonObject config) {
    super(vertx, config);
  }

  @Override
  protected <T> Handler<AsyncResult<HttpResponse<Buffer>>> buildResponseHandler(
      TypeReference<T> returnType, Handler<AsyncResult<T>> handler) {
    return ar -> {
      AsyncResult<T> result;
      if (ar.succeeded()) {
        HttpResponse<Buffer> response = ar.result();
        String respBody = response.bodyAsString();
        String respBodyAbbr = abbreviate(respBody, MAX_ERROR_BODY_LENGTH);
        if (response.statusCode() / 100 == 2) {
          T resultContent;
          try {
            resultContent = DatabindCodec.mapper().readValue(respBody, returnType);
            result = succeededFuture(resultContent);
          } catch (Exception e) {
            if (JsonUtil.isOfType(respBody, SUSHIErrorModel.class) || isJsonArray(respBody)) {
              result = failedFuture(respBodyAbbr);
            } else {
              result = failedFuture(new InvalidReportException(e));
            }
          }
        } else if (response.statusCode() == 429) {
          result = failedFuture(new TooManyRequestsException());
        } else {
          if (StringUtils.isEmpty(respBodyAbbr)) {
            respBodyAbbr = response.statusCode() + " - " + response.statusMessage();
          }
          result = failedFuture(respBodyAbbr);
        }
      } else {
        result = failedFuture(ar.cause());
      }
      handler.handle(result);
    };
  }
}
