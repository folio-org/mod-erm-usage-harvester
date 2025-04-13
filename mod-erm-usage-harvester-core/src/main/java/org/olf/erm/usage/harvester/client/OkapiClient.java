package org.olf.erm.usage.harvester.client;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpResponse;
import java.util.List;
import org.olf.erm.usage.harvester.SystemUser;

public interface OkapiClient {

  Future<String> loginSystemUser(String tenantId, SystemUser systemUser);

  Future<HttpResponse<Buffer>> startHarvester(String tenantId, String token);

  Future<List<String>> getTenants();

  Future<HttpResponse<Buffer>> sendRequest(
      HttpMethod method, String path, String tenantId, String token);
}
