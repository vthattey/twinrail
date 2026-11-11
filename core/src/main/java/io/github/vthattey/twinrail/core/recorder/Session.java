package io.github.vthattey.twinrail.core.recorder;

import java.time.Instant;
import java.util.List;

/**
 * Everything needed to replay a run: the seed, the virtual start time and every event in order.
 *
 * @param formatVersion version of the session file format
 */
public record Session(int formatVersion, long seed, Instant startedAt, List<RecordedEvent> events) {
  /** The session file format version written by this release. */
  public static final int CURRENT_FORMAT_VERSION = 1;

  public Session {
    events = List.copyOf(events);
  }
}
