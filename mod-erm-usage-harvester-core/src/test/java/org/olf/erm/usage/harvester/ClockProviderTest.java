package org.olf.erm.usage.harvester;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.Test;

public class ClockProviderTest {

  @Test
  public void testClockProvider() throws InterruptedException {
    assertThat(ClockProvider.getClock()).isEqualTo(Clock.systemUTC());
    Clock fixedClock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    ClockProvider.setClock(fixedClock);
    assertThat(ClockProvider.getClock()).isEqualTo(fixedClock);
  }
}
