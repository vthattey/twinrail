package io.github.vthattey.twinrail.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.vthattey.twinrail.core.clock.VirtualClock;
import io.github.vthattey.twinrail.core.codec.MessageCodec;
import io.github.vthattey.twinrail.core.codec.ParsedMessage;
import io.github.vthattey.twinrail.core.ids.SeededIds;
import io.github.vthattey.twinrail.core.recorder.Direction;
import io.github.vthattey.twinrail.core.recorder.Recorder;
import io.github.vthattey.twinrail.core.recorder.SessionFiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Same inputs, seed and clock must produce the same messages, ids and session file (CLAUDE.md,
 * "Deterministic").
 */
class DeterminismTest {
  private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");

  private static String template() throws IOException {
    try (InputStream in = DeterminismTest.class.getResourceAsStream("/codec/pacs008-input.xml")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** A small scripted run: two payments, time moves between them. Returns the session file. */
  private static String run(long seed) throws IOException {
    VirtualClock clock = new VirtualClock(START);
    SeededIds ids = new SeededIds(seed);
    Recorder recorder = new Recorder(seed, START);
    MessageCodec codec = new MessageCodec();

    for (int i = 0; i < 2; i++) {
      String inbound =
          template()
              .replace("TWIN-000001", ids.sequence("TWIN"))
              .replace("bdd73226-2feb-4e95-a8ef-e333b266f103", ids.uuid().toString())
              .replace("2026-11-09T10:00:00Z", clock.instant().toString());
      ParsedMessage parsed = codec.parse(inbound);
      recorder.recordMessage(clock.instant(), Direction.INBOUND, parsed.type(), inbound);
      recorder.recordMessage(
          clock.instant(), Direction.OUTBOUND, parsed.type(), codec.write(parsed.mx()));
      Instant now = clock.advance(Duration.ofMinutes(5));
      recorder.recordClockAdvanced(now, Duration.ofMinutes(5));
    }
    return SessionFiles.write(recorder.session());
  }

  @Test
  void sameSeedAndInputsGiveByteIdenticalSessions() throws IOException {
    assertEquals(run(42), run(42));
  }

  @Test
  void aDifferentSeedChangesTheIds() throws IOException {
    assertNotEquals(run(42), run(43));
  }

  @Test
  void replayingTheRecordedInputsReproducesTheRecordedOutputs() throws IOException {
    var session = SessionFiles.read(run(7));
    MessageCodec codec = new MessageCodec();
    var events = session.events();
    for (int i = 0; i < events.size() - 1; i++) {
      if (events.get(i)
              instanceof io.github.vthattey.twinrail.core.recorder.RecordedEvent.Message in
          && in.direction() == Direction.INBOUND) {
        var out =
            (io.github.vthattey.twinrail.core.recorder.RecordedEvent.Message) events.get(i + 1);
        assertEquals(out.payload(), codec.write(codec.parse(in.payload()).mx()));
      }
    }
  }
}
