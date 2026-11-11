package io.github.vthattey.twinrail.core.nacha;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed-width layout of one kind of 94-character record: its fields, in order, with 1-based
 * inclusive positions. Layouts are data; the parser and writer are generic over them.
 *
 * <p>Positions come from public secondary sources (see {@code docs/research.md}, section 7).
 */
public final class Layout {
  /** Every record is this many characters. */
  public static final int RECORD_LENGTH = 94;

  /**
   * One field.
   *
   * @param numeric written right-justified and zero-filled; other fields are left-justified and
   *     space-filled
   */
  public record Field(String name, int start, int end, boolean numeric) {
    public int length() {
      return end - start + 1;
    }
  }

  private final String name;
  private final List<Field> fields;

  private Layout(String name, List<Field> fields) {
    this.name = name;
    this.fields = List.copyOf(fields);
  }

  public String name() {
    return name;
  }

  public List<Field> fields() {
    return fields;
  }

  public Field field(String fieldName) {
    return fields.stream()
        .filter(f -> f.name().equals(fieldName))
        .findFirst()
        .orElseThrow(() ->
            new IllegalArgumentException("Layout " + name + " has no field '" + fieldName + "'."));
  }

  /** Slices a 94-character line into raw (untrimmed) field values, so the line round-trips. */
  Map<String, String> slice(String line) {
    Map<String, String> values = new LinkedHashMap<>();
    for (Field f : fields) {
      values.put(f.name(), line.substring(f.start() - 1, f.end()));
    }
    return values;
  }

  /** Builds the 94-character line. Missing fields are blank (or zero for numeric fields). */
  String format(char recordType, Map<String, String> values) {
    char[] line = new char[RECORD_LENGTH];
    java.util.Arrays.fill(line, ' ');
    line[0] = recordType;
    for (Field f : fields) {
      String raw = values.getOrDefault(f.name(), "");
      if (raw.length() > f.length()) {
        throw new NachaException(
            "Field "
                + name
                + "."
                + f.name()
                + " is "
                + raw.length()
                + " characters but the layout allows "
                + f.length()
                + ". Shorten the value.");
      }
      String padded =
          f.numeric()
              ? "0".repeat(f.length() - raw.length()) + raw
              : raw + " ".repeat(f.length() - raw.length());
      padded.getChars(0, padded.length(), line, f.start() - 1);
    }
    return new String(line);
  }

  static Builder named(String name) {
    return new Builder(name);
  }

  /** Small DSL for the tables in {@link Layouts}. */
  static final class Builder {
    private final String name;
    private final List<Field> fields = new ArrayList<>();

    private Builder(String name) {
      this.name = name;
    }

    Builder text(String field, int start, int end) {
      fields.add(new Field(field, start, end, false));
      return this;
    }

    Builder number(String field, int start, int end) {
      fields.add(new Field(field, start, end, true));
      return this;
    }

    Layout build() {
      return new Layout(name, fields);
    }
  }
}
