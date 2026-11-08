package io.github.vthattey.twinrail.core.recorder;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.vthattey.twinrail.core.codec.CodecException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes session files: JSON with a format version, identical text for the same session.
 */
public final class SessionFiles {
  private SessionFiles() {}

  /** Serialises a session. The same session always produces the same text. */
  public static String write(Session session) {
    JsonObject root = new JsonObject();
    root.addProperty("formatVersion", session.formatVersion());
    root.addProperty("seed", session.seed());
    root.addProperty("startedAt", session.startedAt().toString());
    JsonArray events = new JsonArray();
    for (RecordedEvent event : session.events()) {
      JsonObject o = new JsonObject();
      o.addProperty("seq", event.seq());
      o.addProperty("at", event.at().toString());
      switch (event) {
        case RecordedEvent.Message m -> {
          o.addProperty("kind", "message");
          o.addProperty("direction", m.direction().name());
          if (m.type() != null) {
            o.addProperty("type", m.type());
          }
          o.addProperty("payload", m.payload());
        }
        case RecordedEvent.ClockAdvanced c -> {
          o.addProperty("kind", "clockAdvanced");
          o.addProperty("by", c.by().toString());
        }
        case RecordedEvent.ClockSet c -> o.addProperty("kind", "clockSet");
      }
      events.add(o);
    }
    root.add("events", events);
    return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
  }

  /**
   * Parses a session file.
   *
   * @throws CodecException if the file is not valid, or was written by another format version
   */
  public static Session read(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      int version = root.get("formatVersion").getAsInt();
      if (version != Session.CURRENT_FORMAT_VERSION) {
        throw new CodecException(
            "Session file format version "
                + version
                + " is not supported by this release (supported: "
                + Session.CURRENT_FORMAT_VERSION
                + "). Export the session again with a matching Twinrail version.");
      }
      List<RecordedEvent> events = new ArrayList<>();
      for (JsonElement e : root.getAsJsonArray("events")) {
        events.add(readEvent(e.getAsJsonObject()));
      }
      return new Session(
          version,
          root.get("seed").getAsLong(),
          Instant.parse(root.get("startedAt").getAsString()),
          events);
    } catch (CodecException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new CodecException(
          "The session file could not be read ("
              + e.getClass().getSimpleName()
              + "). Check that it was exported by Twinrail and has not been edited.",
          e);
    }
  }

  private static RecordedEvent readEvent(JsonObject o) {
    long seq = o.get("seq").getAsLong();
    Instant at = Instant.parse(o.get("at").getAsString());
    String kind = o.get("kind").getAsString();
    return switch (kind) {
      case "message" ->
          new RecordedEvent.Message(
              seq,
              at,
              Direction.valueOf(o.get("direction").getAsString()),
              o.has("type") ? o.get("type").getAsString() : null,
              o.get("payload").getAsString());
      case "clockAdvanced" ->
          new RecordedEvent.ClockAdvanced(seq, at, Duration.parse(o.get("by").getAsString()));
      case "clockSet" -> new RecordedEvent.ClockSet(seq, at);
      default ->
          throw new CodecException(
              "Unknown event kind '" + kind + "' in the session file. Export the session again.");
    };
  }
}
