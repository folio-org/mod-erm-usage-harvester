package org.olf.erm.usage.harvester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebClientProviderTest {

  private Vertx vertx;

  @BeforeEach
  void setUp() {
    WebClientProvider.reset();
    vertx = Vertx.vertx();
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    if (vertx != null) {
      CountDownLatch latch = new CountDownLatch(1);
      vertx.close().onComplete(ar -> latch.countDown());
      latch.await(5, TimeUnit.SECONDS);
    }
    WebClientProvider.reset();
  }

  @Test
  void returnsSameInstanceForSameVertx() {
    WebClient client1 = WebClientProvider.get(vertx);
    WebClient client2 = WebClientProvider.get(vertx);

    assertThat(client1).isSameAs(client2);
  }

  @Test
  void returnsDifferentInstanceForDifferentVertx() throws InterruptedException {
    Vertx vertx2 = Vertx.vertx();
    try {
      WebClient client1 = WebClientProvider.get(vertx);
      WebClient client2 = WebClientProvider.get(vertx2);

      assertThat(client1).isNotSameAs(client2);
    } finally {
      CountDownLatch latch = new CountDownLatch(1);
      vertx2.close().onComplete(ar -> latch.countDown());
      latch.await(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void resetClearsCachedInstances() {
    WebClient client1 = WebClientProvider.get(vertx);
    WebClientProvider.reset();
    WebClient client2 = WebClientProvider.get(vertx);

    assertThat(client1).isNotSameAs(client2);
  }

  @Test
  void throwsOnNullVertx() {
    assertThatThrownBy(() -> WebClientProvider.get(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("vertx must not be null");
  }
}
