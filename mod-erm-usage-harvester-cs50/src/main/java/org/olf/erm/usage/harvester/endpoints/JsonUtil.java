package org.olf.erm.usage.harvester.endpoints;

import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.json.Json;

public class JsonUtil {

  private JsonUtil() {}

  public static boolean isJsonArray(String json) {
    try {
      JsonNode jsonNode = Json.decodeValue(json, JsonNode.class);
      return jsonNode.isArray();
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isOfType(String json, Class<?> clazz) {
    try {
      Json.decodeValue(json, clazz);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
