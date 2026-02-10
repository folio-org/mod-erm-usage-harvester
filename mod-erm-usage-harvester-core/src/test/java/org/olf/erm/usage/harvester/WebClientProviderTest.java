package org.olf.erm.usage.harvester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class WebClientProviderTest {

  @Test
  void returnsSameInstanceForSameVertx(Vertx vertx) {
    WebClient client1 = WebClientProvider.get(vertx);
    WebClient client2 = WebClientProvider.get(vertx);

    assertThat(client1).isSameAs(client2);
  }

  @Test
  void returnsDifferentInstanceForDifferentVertx(Vertx vertx, VertxTestContext ctx) {
    Vertx vertx2 = Vertx.vertx();
    WebClient client1 = WebClientProvider.get(vertx);
    WebClient client2 = WebClientProvider.get(vertx2);

    assertThat(client1).isNotSameAs(client2);
    vertx2.close().onComplete(ctx.succeedingThenComplete());
  }

  @Test
  void throwsOnNullVertx() {
    assertThatThrownBy(() -> WebClientProvider.get(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("vertx must not be null");
  }
}
