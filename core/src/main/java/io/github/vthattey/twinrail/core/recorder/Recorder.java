package io.github.vthattey.twinrail.core.recorder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Keeps every message and clock movement in memory, in order, so a run can be exported and
 * replayed.
 */
public final class Recorder {
  private final long seed;
  private final Instant startedAt;
  private final List<RecordedEvent> events = new ArrayList<>();

  public Recorder(long seed, Instant startedAt) {
    this.seed = seed;
    this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
  }

  public synchronized RecordedEvent.Message recordMessage(
      Instant at, Direction direction, String type, String payload) {
    var event = new RecordedEvent.Message(next(), at, direction, type, payload);
    events.add(event);
    return event;
  }

  public synchronized RecordedEvent.ClockAdvanced recordClockAdvanced(Instant at, Duration by) {
    var event = new RecordedEvent.ClockAdvanced(next(), at, by);
    events.add(event);
    return event;
  }

  public synchronized RecordedEvent.ClockSet recordClockSet(Instant at) {
    var event = new RecordedEvent.ClockSet(next(), at);
    events.add(event);
    return event;
  }

  /** A snapshot of the run so far. */
  public synchronized Session session() {
    return new Session(Session.CURRENT_FORMAT_VERSION, seed, startedAt, List.copyOf(events));
  }

  private long next() {
    return events.size() + 1L;
  }
}
