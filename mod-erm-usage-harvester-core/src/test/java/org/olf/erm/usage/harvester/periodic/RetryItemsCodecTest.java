package org.olf.erm.usage.harvester.periodic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.worker.WorkerVerticle.ParkedItem;

/** Test class for {@link RetryItemsCodec} */
class RetryItemsCodecTest {

  private static final List<ParkedItem> ITEMS =
      List.of(
          new ParkedItem(
              new FetchItem("TR", "2025-01-01", "2025-01-31"),
              1,
              Instant.parse("2025-02-01T10:30:00.123456789Z"),
              "1011 Report Queued for Processing"),
          new ParkedItem(
              new FetchItem("DR", "2024-03-01", "2024-03-31"),
              3,
              Instant.parse("2025-02-01T11:00:00Z"),
              null));

  @Test
  void testRoundTrip() {
    assertThat(RetryItemsCodec.decode(RetryItemsCodec.encode(ITEMS)))
        .as("... all fields and the order are kept, a missing reason stays null")
        .containsExactlyElementsOf(ITEMS);
  }

  @Test
  void testRoundTripOfEmptyList() {
    assertThat(RetryItemsCodec.decode(RetryItemsCodec.encode(List.of()))).isEmpty();
  }

  @Test
  void testDecodedListIsImmutable() {
    final var decoded = RetryItemsCodec.decode(RetryItemsCodec.encode(ITEMS));

    assertThatThrownBy(decoded::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testUnknownFieldsAreIgnored() {
    final var json =
        """
        {"version":1,"extra":true,"items":[{"reportType":"TR","begin":"2025-01-01",\
        "end":"2025-01-31","retryCount":1,"dueAt":"2025-02-01T10:30:00Z","other":"x"}]}
        """;

    assertThat(RetryItemsCodec.decode(json))
        .containsExactly(
            new ParkedItem(
                new FetchItem("TR", "2025-01-01", "2025-01-31"),
                1,
                Instant.parse("2025-02-01T10:30:00Z"),
                null));
  }

  @Test
  void testUnsupportedVersion() {
    assertThatThrownBy(() -> RetryItemsCodec.decode("{\"version\":2,\"items\":[]}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
    assertThatThrownBy(() -> RetryItemsCodec.decode("{\"items\":[]}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testInvalidJson() {
    assertThatThrownBy(() -> RetryItemsCodec.decode("not json"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void testMissingRequiredField() {
    final var json =
        """
        {"version":1,"items":[{"reportType":"TR","begin":"2025-01-01",\
        "end":"2025-01-31","retryCount":1}]}
        """;

    assertThatThrownBy(() -> RetryItemsCodec.decode(json))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
