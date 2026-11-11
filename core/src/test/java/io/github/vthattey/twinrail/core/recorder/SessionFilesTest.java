package io.github.vthattey.twinrail.core.recorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.vthattey.twinrail.core.codec.CodecException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionFilesTest {
  private static final Instant START = Instant.parse("2026-11-09T10:00:00Z");

  private static Session sample() {
    Recorder recorder = new Recorder(42, START);
    recorder.recordMessage(
        START,
        Direction.INBOUND,
        "pacs.008.001.08",
        "<Document>\n  \"quoted\" & <b/>\n</Document>");
    recorder.recordClockAdvanced(START.plusSeconds(30), Duration.ofSeconds(30));
    recorder.recordMessage(START.plusSeconds(30), Direction.OUTBOUND, null, "not xml: <<");
    recorder.recordClockSet(START.plusSeconds(3600));
    return recorder.session();
  }

  @Test
  void roundTripsEveryEventKind() {
    Session session = sample();
    assertEquals(session, SessionFiles.read(SessionFiles.write(session)));
  }

  @Test
  void eventsAreNumberedFromOneInOrder() {
    var events = sample().events();
    for (int i = 0; i < events.size(); i++) {
      assertEquals(i + 1L, events.get(i).seq());
    }
  }

  @Test
  void writingTheSameSessionTwiceGivesIdenticalText() {
    assertEquals(SessionFiles.write(sample()), SessionFiles.write(sample()));
  }

  @Test
  void keepsPayloadsVerbatimWithoutHtmlEscaping() {
    String text = SessionFiles.write(sample());
    assertFalse(text.contains("\\u003c"));
    assertEquals(
        "<Document>\n  \"quoted\" & <b/>\n</Document>",
        ((RecordedEvent.Message) SessionFiles.read(text).events().get(0)).payload());
  }

  @Test
  void rejectsOtherFormatVersionsAndSaysWhatToDo() {
    String text =
        SessionFiles.write(sample()).replace("\"formatVersion\": 1", "\"formatVersion\": 99");
    CodecException e = assertThrows(CodecException.class, () -> SessionFiles.read(text));
    assertEquals(
        "Session file format version 99 is not supported by this release (supported: 1). "
            + "Export the session again with a matching Twinrail version.",
        e.getMessage());
  }

  @Test
  void rejectsGarbageWithoutEchoingIt() {
    CodecException e =
        assertThrows(CodecException.class, () -> SessionFiles.read("secret garbage"));
    assertFalse(e.getMessage().contains("secret"));
  }
}
