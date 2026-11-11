package io.github.vthattey.twinrail.core.scenario;

import io.github.vthattey.twinrail.core.definition.DefinitionException;
import io.github.vthattey.twinrail.core.definition.Fields;
import io.github.vthattey.twinrail.core.definition.Yamls;
import io.github.vthattey.twinrail.core.matcher.FieldMatcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads scenario files from YAML (see {@code docs/scenarios.md}). Loading is strict. */
public final class ScenarioLoader {
  /** The format version this loader reads. */
  public static final int FORMAT = 1;

  private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
  private static final Pattern MESSAGE_TYPE =
      Pattern.compile("[a-z]{4}\\.[0-9]{3}\\.[0-9]{3}\\.[0-9]{2}|nacha\\.entry");
  private static final Pattern RETURN_CODE = Pattern.compile("R[0-9]{2}");
  private static final Pattern CHANGE_CODE = Pattern.compile("C[0-9]{2}");

  /** The corrected values each supported change code needs (docs/nacha.md). */
  private static final Map<String, List<String>> CORRECTED =
      Map.of(
          "C01", List.of("account"),
          "C02", List.of("routing"),
          "C03", List.of("routing", "account"),
          "C05", List.of("transactionCode"),
          "C06", List.of("account", "transactionCode"),
          "C07", List.of("routing", "account", "transactionCode"),
          "C09", List.of("individualId"));

  private static final Pattern REASON = Pattern.compile("[A-Z0-9]{2,4}");

  private ScenarioLoader() {}

  public static ScenarioSet loadFile(Path file) {
    try {
      return load(Files.readString(file, StandardCharsets.UTF_8), file.getFileName().toString());
    } catch (IOException e) {
      throw new DefinitionException(
          "Cannot read scenario file " + file + ". Check that the file exists and is readable.", e);
    }
  }

  /**
   * @param name the file name used in error messages
   * @throws DefinitionException if the file is invalid
   */
  public static ScenarioSet load(String yaml, String name) {
    Fields root = Fields.of(Yamls.load(yaml, name), "Scenario file " + name);
    Object format = root.raw("format");
    if (!Integer.valueOf(FORMAT).equals(format)) {
      throw new DefinitionException(
          root.where()
              + " must start with 'format: "
              + FORMAT
              + "' (found "
              + format
              + "). Add it, or upgrade Twinrail if the file uses a newer format.");
    }
    List<Object> items = root.requireList("scenarios");
    root.finish();
    List<Scenario> scenarios = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < items.size(); i++) {
      Scenario scenario = readScenario(items.get(i), i, name);
      if (!seen.add(scenario.id())) {
        throw new DefinitionException(
            name + " has two scenarios with the id " + scenario.id() + ". Ids must be unique.");
      }
      scenarios.add(scenario);
    }
    return new ScenarioSet(scenarios);
  }

  private static Scenario readScenario(Object node, int index, String file) {
    Fields f = Fields.of(node, "Scenario #" + (index + 1) + " in " + file);
    String id = f.requireString("id");
    if (!ID.matcher(id).matches()) {
      throw new DefinitionException(
          f.where()
              + ": id '"
              + id
              + "' is not valid. Use letters, digits, '.', '_' or '-', up to 64 characters.");
    }
    Fields s = Fields.of(node, "Scenario " + id + " in " + file);
    s.raw("id");
    String description = s.optionalString("description").orElse(null);
    String message = null;
    List<FieldMatcher> where = new ArrayList<>();
    var match = s.optionalMap("match");
    if (match.isPresent()) {
      Fields m = match.get();
      message = m.optionalString("message").orElse(null);
      if (message != null && !MESSAGE_TYPE.matcher(message).matches()) {
        throw new DefinitionException(
            m.where()
                + ": message '"
                + message
                + "' is not a message identifier. Use the form pacs.008.001.08, or nacha.entry for ACH entries.");
      }
      for (Object w : m.optionalList("where").orElse(List.of())) {
        where.add(FieldMatcher.read(Fields.of(w, m.where() + ", 'where' entry")));
      }
      m.finish();
    }
    Action action = readAction(s.requireMap("action"));
    s.finish();
    return new Scenario(id, description, message, where, action);
  }

  private static Action readAction(Fields a) {
    String type = a.requireString("type");
    Action action =
        switch (type) {
          case "reject" -> {
            String reason = a.requireString("reason");
            if (!REASON.matcher(reason).matches()) {
              throw new DefinitionException(
                  a.where()
                      + ": reason '"
                      + reason
                      + "' is not a reason code. Use 2 to 4 capital letters or digits, for example"
                      + " AC04.");
            }
            yield new Action.Reject(
                reason, a.optionalString("text").orElse("Rejected by scenario"));
          }
          case "delay" -> new Action.Delay(readDuration(a));
          case "drop" -> new Action.Drop();
          case "return" -> readReturn(a);
          case "noc" -> readNoc(a);
          default ->
              throw new DefinitionException(
                  a.where()
                      + ": unknown action type '"
                      + type
                      + "'. Use reject, delay, drop, return or noc.");
        };
    a.finish();
    return action;
  }

  private static Action readReturn(Fields a) {
    String code = a.requireString("code");
    if (!RETURN_CODE.matcher(code).matches()) {
      throw new DefinitionException(
          a.where()
              + ": return code '"
              + code
              + "' is not a return code. Use R followed by two digits, for example R01.");
    }
    Duration after = a.has("after") ? readDuration(a, "after", true) : Duration.ZERO;
    return new Action.Return(code, after, a.optionalString("text").orElse(null));
  }

  private static Action readNoc(Fields a) {
    String code = a.requireString("code");
    if (!CHANGE_CODE.matcher(code).matches()) {
      throw new DefinitionException(
          a.where()
              + ": change code '"
              + code
              + "' is not a change code. Use C followed by two digits, for example C01.");
    }
    List<String> needed = CORRECTED.get(code);
    if (needed == null) {
      throw new DefinitionException(
          a.where()
              + ": change code "
              + code
              + " is not supported. Supported: "
              + CORRECTED.keySet().stream().sorted().toList()
              + " (other codes need a corrected-data layout that is not in Twinrail's sources yet).");
    }
    Map<String, String> corrected = new java.util.LinkedHashMap<>();
    for (String field : needed) {
      corrected.put(field, a.requireString(field));
    }
    return new Action.Noc(code, corrected);
  }

  private static Duration readDuration(Fields a) {
    return readDuration(a, "duration", false);
  }

  private static Duration readDuration(Fields a, String key, boolean allowZero) {
    String text = a.requireString(key);
    try {
      Duration duration = Duration.parse(text);
      if (duration.isNegative() || (duration.isZero() && !allowZero)) {
        throw new DefinitionException(
            a.where()
                + ": "
                + key
                + " '"
                + text
                + "' must be "
                + (allowZero ? "zero or more" : "greater than zero")
                + ", for example PT30S.");
      }
      return duration;
    } catch (DateTimeParseException e) {
      throw new DefinitionException(
          a.where()
              + ": "
              + key
              + " '"
              + text
              + "' is not an ISO 8601 duration such as PT30S or PT2M.",
          e);
    }
  }
}
