package io.github.vthattey.twinrail.core.nacha;

import io.github.vthattey.twinrail.core.definition.DefinitionException;
import io.github.vthattey.twinrail.core.definition.Fields;
import io.github.vthattey.twinrail.core.definition.Yamls;
import io.github.vthattey.twinrail.core.rules.Source;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The return (R) and notification-of-change (C) codes the emulator knows, loaded from a data file
 * (see {@code codes/nacha-codes-1.yaml}). Every code records the source it came from.
 */
public final class ReasonCodes {
  private static final Pattern RETURN = Pattern.compile("R[0-9]{2}");
  private static final Pattern CHANGE = Pattern.compile("C[0-9]{2}");

  /**
   * One code.
   *
   * @param timeframe the return time frame as the source states it; {@code null} for change codes
   */
  public record Code(String code, String title, String timeframe, Source source) {}

  private final Map<String, Code> returns;
  private final Map<String, Code> changes;

  private ReasonCodes(Map<String, Code> returns, Map<String, Code> changes) {
    this.returns = Map.copyOf(returns);
    this.changes = Map.copyOf(changes);
  }

  /**
   * @param name the file name used in error messages
   * @throws DefinitionException if the table is invalid
   */
  public static ReasonCodes load(String yaml, String name) {
    Fields root = Fields.of(Yamls.load(yaml, name), "Code table " + name);
    if (!Integer.valueOf(1).equals(root.raw("format"))) {
      throw new DefinitionException(name + " must start with 'format: 1'.");
    }
    root.requireMap("table"); // id, name and version document the table; they are not interpreted
    Map<String, Code> returns = read(root.requireList("returns"), RETURN, true, name);
    Map<String, Code> changes = read(root.requireList("changes"), CHANGE, false, name);
    root.finish();
    return new ReasonCodes(returns, changes);
  }

  private static Map<String, Code> read(
      List<Object> items, Pattern shape, boolean timeframe, String file) {
    Map<String, Code> codes = new LinkedHashMap<>();
    for (Object item : items) {
      Fields f = Fields.of(item, "Code entry in " + file);
      String code = f.requireString("code");
      if (!shape.matcher(code).matches()) {
        throw new DefinitionException(
            file + ": '" + code + "' is not a valid code. Use the form " + shape.pattern() + ".");
      }
      String title = f.requireString("title");
      String frame = timeframe ? f.requireString("timeframe") : null;
      Fields s = f.requireMap("source");
      Source source =
          new Source(
              s.requireString("spec"),
              s.requireString("version"),
              s.optionalString("section").orElse(null));
      s.finish();
      f.finish();
      if (codes.put(code, new Code(code, title, frame, source)) != null) {
        throw new DefinitionException(file + " lists " + code + " twice. Codes must be unique.");
      }
    }
    return codes;
  }

  public Code returnCode(String code) {
    return returns.get(code);
  }

  public Code changeCode(String code) {
    return changes.get(code);
  }

  /** Sorted list of known return codes, for error messages. */
  public List<String> returnCodes() {
    return returns.keySet().stream().sorted().toList();
  }

  public List<String> changeCodes() {
    return changes.keySet().stream().sorted().toList();
  }
}
