package io.github.vthattey.twinrail.core.clock;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The emulator's notion of "now". It never reads the wall clock and never waits: time only moves
 * when {@link #advance} or {@link #set} is called, so cut-offs, holidays and time-outs are
 * reproducible.
 */
public final class VirtualClock implements InstantSource {
  private final AtomicReference<Instant> now;

  public VirtualClock(Instant start) {
    this.now = new AtomicReference<>(java.util.Objects.requireNonNull(start, "start"));
  }

  @Override
  public Instant instant() {
    return now.get();
  }

  /** Moves time forward by {@code by} and returns the new time. */
  public Instant advance(Duration by) {
    java.util.Objects.requireNonNull(by, "by");
    if (by.isNegative()) {
      throw new IllegalArgumentException(
          "Cannot advance the clock by a negative duration (" + by + "). Use a positive duration.");
    }
    return now.updateAndGet(current -> current.plus(by));
  }

  /** Moves time to {@code target}, which must not be earlier than the current time. */
  public Instant set(Instant target) {
    java.util.Objects.requireNonNull(target, "target");
    return now.updateAndGet(current -> {
      if (target.isBefore(current)) {
        throw new IllegalArgumentException(
            "Cannot move the clock back from "
                + current
                + " to "
                + target
                + ". Time only moves forward; reset the emulator to start again.");
      }
      return target;
    });
  }
}
