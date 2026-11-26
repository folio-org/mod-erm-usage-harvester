package org.olf.erm.usage.harvester.periodic;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.worker.WorkerVerticle.ParkedItem;

/**
 * Converts parked items to and from a JSON string. Quartz keeps them in a job's {@code JobDataMap},
 * which must only hold strings, because the Hazelcast job store serializes it.
 *
 * <p>The format carries a version, so it can change later; unknown fields are ignored.
 */
final class RetryItemsCodec {

  static final int VERSION = 1;

  private static final String VERSION_KEY = "version";
  private static final String ITEMS = "items";
  private static final String REPORT_TYPE = "reportType";
  private static final String BEGIN = "begin";
  private static final String END = "end";
  private static final String RETRY_COUNT = "retryCount";
  private static final String DUE_AT = "dueAt";
  private static final String REASON = "reason";

  /**
   * @param items the parked items
   * @return the items as a JSON string
   */
  static String encode(final List<ParkedItem> items) {
    final var array = new JsonArray();
    items.forEach(item -> array.add(toJson(item)));
    return new JsonObject().put(VERSION_KEY, VERSION).put(ITEMS, array).encode();
  }

  /**
   * @param json a JSON string created by {@link #encode(List)}
   * @return the parked items, in their original order
   * @throws IllegalArgumentException if the string is not valid, or has an unsupported version
   */
  static List<ParkedItem> decode(final String json) {
    try {
      final var object = new JsonObject(json);
      final var version = object.getInteger(VERSION_KEY);
      if (!Objects.equals(version, VERSION)) {
        throw new IllegalArgumentException("Unsupported retry items format version: " + version);
      }
      return object.getJsonArray(ITEMS).stream()
          .map(JsonObject.class::cast)
          .map(RetryItemsCodec::fromJson)
          .toList();
    } catch (DecodeException
        | ClassCastException
        | NullPointerException
        | DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid retry items: " + json, e);
    }
  }

  private static JsonObject toJson(final ParkedItem item) {
    final var json =
        new JsonObject()
            .put(REPORT_TYPE, item.item().reportType())
            .put(BEGIN, item.item().begin())
            .put(END, item.item().end())
            .put(RETRY_COUNT, item.retryCount())
            .put(DUE_AT, item.dueAt().toString());
    if (item.reason() != null) {
      json.put(REASON, item.reason());
    }
    return json;
  }

  private static ParkedItem fromJson(final JsonObject json) {
    final var item =
        new FetchItem(
            Objects.requireNonNull(json.getString(REPORT_TYPE), REPORT_TYPE),
            Objects.requireNonNull(json.getString(BEGIN), BEGIN),
            Objects.requireNonNull(json.getString(END), END));
    return new ParkedItem(
        item,
        Objects.requireNonNull(json.getInteger(RETRY_COUNT), RETRY_COUNT),
        Instant.parse(Objects.requireNonNull(json.getString(DUE_AT), DUE_AT)),
        json.getString(REASON));
  }

  private RetryItemsCodec() {}
}
