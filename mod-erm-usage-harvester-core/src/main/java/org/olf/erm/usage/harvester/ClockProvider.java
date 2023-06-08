package org.olf.erm.usage.harvester;

import java.time.Clock;

public class ClockProvider {

  private ClockProvider() {}

  private static Clock clock = Clock.systemUTC();

  public static Clock getClock() {
    return clock;
  }

  public static void setClock(Clock clock) {
    ClockProvider.clock = clock;
  }
}
