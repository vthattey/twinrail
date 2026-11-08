package io.github.vthattey.twinrail.core.rules;

import io.github.vthattey.twinrail.core.definition.DefinitionException;
import io.github.vthattey.twinrail.core.definition.Fields;
import io.github.vthattey.twinrail.core.definition.Yamls;
import io.github.vthattey.twinrail.core.matcher.FieldMatcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads rule packs from YAML (see {@code docs/rule-packs.md}). Loading is strict: unknown keys,
 * missing sources and bad paths are errors that name the file and the rule.
 */
public final class RulePackLoader {
  /** The format version this loader reads. */
  public static final int FORMAT = 1;

  static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
  static final Pattern MESSAGE_TYPE = Pattern.compile("[a-z]{4}\\.[0-9]{3}\\.[0-9]{3}\\.[0-9]{2}");
  static final Pattern REASON = Pattern.compile("[A-Z0-9]{2,4}");

  private RulePackLoader() {}

  public static RulePack loadFile(Path file) {
    try {
      return load(Files.readString(file, StandardCharsets.UTF_8), file.getFileName().toString());
    } catch (IOException e) {
      throw new DefinitionException(
          "Cannot read rule pack " + file + ". Check that the file exists and is readable.", e);
    }
  }

  /**
   * @param name the file name used in error messages
   * @throws DefinitionException if the pack is invalid
   */
  public static RulePack load(String yaml, String name) {
    Fields root = Fields.of(Yamls.load(yaml, name), "Rule pack " + name);
    checkFormat(root);
    Fields pack = root.requireMap("pack");
    String id = readId(pack, "pack id");
    String packName = pack.requireString("name");
    String version = pack.requireString("version");
    pack.finish();
    List<Object> items = root.requireList("rules");
    root.finish();
    if (items.isEmpty()) {
      throw new DefinitionException(name + " has no rules. Add at least one entry under 'rules'.");
    }
    List<Rule> rules = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < items.size(); i++) {
      Rule rule = readRule(items.get(i), i, name);
      if (!seen.add(rule.id())) {
        throw new DefinitionException(
            name + " has two rules with the id " + rule.id() + ". Rule ids must be unique.");
      }
      rules.add(rule);
    }
    return new RulePack(id, packName, version, rules);
  }

  static void checkFormat(Fields root) {
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
  }

  private static String readId(Fields f, String what) {
    String id = f.requireString("id");
    if (!ID.matcher(id).matches()) {
      throw new DefinitionException(
          f.where()
              + ": "
              + what
              + " '"
              + id
              + "' is not valid. Use letters, digits, '.', '_' or '-', up to 64 characters.");
    }
    return id;
  }

  private static Rule readRule(Object node, int index, String file) {
    Fields f = Fields.of(node, "Rule #" + (index + 1) + " in " + file);
    String id = readId(f, "rule id");
    Fields r = Fields.of(node, "Rule " + id + " in " + file);
    r.raw("id");
    String message = r.requireString("message");
    if (!MESSAGE_TYPE.matcher(message).matches()) {
      throw new DefinitionException(
          r.where()
              + ": message '"
              + message
              + "' is not a message identifier. Use the form pacs.008.001.08.");
    }
    Source source = readSource(r);
    List<FieldMatcher> when = new ArrayList<>();
    for (Object w : r.optionalList("when").orElse(List.of())) {
      when.add(FieldMatcher.read(Fields.of(w, r.where() + ", 'when' entry")));
    }
    FieldMatcher assertion = FieldMatcher.read(r.requireMap("assert"));
    String reason = r.requireString("reason");
    if (!REASON.matcher(reason).matches()) {
      throw new DefinitionException(
          r.where()
              + ": reason '"
              + reason
              + "' is not a reason code. Use 2 to 4 capital letters or digits, for example AM01.");
    }
    String text = r.requireString("text");
    Severity severity = readSeverity(r);
    r.finish();
    return new Rule(id, message, source, when, assertion, reason, text, severity);
  }

  private static Source readSource(Fields r) {
    Fields s;
    try {
      s = r.requireMap("source");
    } catch (DefinitionException e) {
      throw new DefinitionException(
          r.where()
              + " has no 'source'. Every rule records the public specification it comes from: "
              + "source: {spec: \"...\", version: \"...\"}.",
          e);
    }
    String spec = s.requireString("spec");
    String version = s.requireString("version");
    String section = s.optionalString("section").orElse(null);
    s.finish();
    return new Source(spec, version, section);
  }

  private static Severity readSeverity(Fields r) {
    String text = r.optionalString("severity").orElse("error");
    return switch (text.toLowerCase(Locale.ROOT)) {
      case "error" -> Severity.ERROR;
      case "warning" -> Severity.WARNING;
      default ->
          throw new DefinitionException(
              r.where() + ": severity '" + text + "' must be 'error' or 'warning'.");
    };
  }
}
