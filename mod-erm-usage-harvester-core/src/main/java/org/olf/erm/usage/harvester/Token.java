package org.olf.erm.usage.harvester;

import java.util.Base64;
import java.util.Objects;
import io.vertx.core.json.JsonObject;

public class Token {

  private final String token;
  private final String userId;
  private final String tenantId;


  public String getToken() {
    return token;
  }

  public String getUserId() {
    return userId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public Token(String token) {
    Objects.requireNonNull(token);
    this.token = token;
    JsonObject json;
    try {
      String decoded = new String(Base64.getDecoder().decode(token.split("\\.")[1]));
      json = new JsonObject(decoded);
    } catch (Exception e) {
      e.printStackTrace();
      throw new IllegalArgumentException(e);
    }

    this.userId = json.getString("user_id");
    this.tenantId = json.getString("tenant");
    Objects.requireNonNull(userId);
    Objects.requireNonNull(tenantId);
  }

  public static Token createDummy(String token, String userId, String tenantId) {
    return new Token(token, userId, tenantId);
  }

  private Token(String token, String userId, String tenantId) {
    this.token = token;
    this.userId = userId;
    this.tenantId = tenantId;
  }
}
