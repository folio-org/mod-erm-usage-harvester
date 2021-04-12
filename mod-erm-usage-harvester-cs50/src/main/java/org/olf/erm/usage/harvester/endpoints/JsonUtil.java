package org.olf.erm.usage.harvester.endpoints;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import io.vertx.core.json.Json;

public class JsonUtil {

  private static final ObjectMapper om =
      new ObjectMapper()
          .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
          .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
          .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES, true)
          .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

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
      om.readValue(json, clazz);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
