package org.olf.erm.usage.harvester;

import io.vertx.core.json.JsonObject;

public class SystemUser {

  private final String username;
  private final String password;

  public SystemUser(String tenantId) {
    username = System.getenv(tenantId.toUpperCase() + "_USER_NAME");
    password = System.getenv(tenantId.toUpperCase() + "_USER_PASS");
  }

  public SystemUser(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public JsonObject toJsonObject() {
    return new JsonObject().put("username", username).put("password", password);
  }
}
