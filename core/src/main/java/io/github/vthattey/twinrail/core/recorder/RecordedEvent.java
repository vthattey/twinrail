package io.github.vthattey.twinrail.core.recorder;

import java.time.Duration;
import java.time.Instant;

/** One entry in the session log. Entries are numbered from 1 in the order they happened. */
public sealed interface RecordedEvent {
  long seq();

  /** Virtual time at which the event happened. */
  Instant at();

  /**
   * A message crossed the emulator boundary.
   *
   * @param type the message identifier such as {@code pacs.008.001.08}, or {@code null} if the
   *     payload could not be parsed
   * @param payload the message exactly as received or sent
   */
  record Message(long seq, Instant at, Direction direction, String type, String payload)
      implements RecordedEvent {}

  /** The virtual clock was moved forward by {@code by}; {@code at} is the new time. */
  record ClockAdvanced(long seq, Instant at, Duration by) implements RecordedEvent {}

  /** The virtual clock was set; {@code at} is the new time. */
  record ClockSet(long seq, Instant at) implements RecordedEvent {}
}
