package io.github.vthattey.twinrail.core.clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VirtualClockTest {
  private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");

  @Test
  void doesNotMoveOnItsOwn() throws InterruptedException {
    VirtualClock clock = new VirtualClock(START);
    Thread.sleep(20);
    assertEquals(START, clock.instant());
  }

  @Test
  void advancesAndSetsForward() {
    VirtualClock clock = new VirtualClock(START);
    assertEquals(START.plusSeconds(90), clock.advance(Duration.ofSeconds(90)));
    Instant target = START.plus(Duration.ofHours(2));
    assertEquals(target, clock.set(target));
    assertEquals(target, clock.instant());
  }

  @Test
  void rejectsGoingBackwardsWithAnActionableMessage() {
    VirtualClock clock = new VirtualClock(START);
    IllegalArgumentException negative =
        assertThrows(IllegalArgumentException.class, () -> clock.advance(Duration.ofSeconds(-1)));
    assertEquals(
        "Cannot advance the clock by a negative duration (PT-1S). Use a positive duration.",
        negative.getMessage());
    IllegalArgumentException back =
        assertThrows(IllegalArgumentException.class, () -> clock.set(START.minusSeconds(1)));
    assertEquals(
        "Cannot move the clock back from 2026-11-09T10:00:00Z to 2026-11-09T09:59:59Z. "
            + "Time only moves forward; reset the emulator to start again.",
        back.getMessage());
    assertEquals(START, clock.instant());
  }
}
