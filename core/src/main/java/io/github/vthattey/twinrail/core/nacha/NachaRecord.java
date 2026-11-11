package io.github.vthattey.twinrail.core.nacha;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One record of an ACH file: its layout, the raw field values and the 1-based line it was read from
 * (0 for records built in memory).
 */
public final class NachaRecord {
  private final Layout layout;
  private final char type;
  private final Map<String, String> values;
  private final int line;

  NachaRecord(Layout layout, char type, Map<String, String> values, int line) {
    this.layout = layout;
    this.type = type;
    this.values = new LinkedHashMap<>(values);
    this.line = line;
  }

  public static NachaRecord of(Layout layout, char type, Map<String, String> values) {
    return new NachaRecord(layout, type, values, 0);
  }

  public Layout layout() {
    return layout;
  }

  /** The record type code, the first character of the line. */
  public char type() {
    return type;
  }

  public int line() {
    return line;
  }

  /** Field value with surrounding blanks removed. Empty if the field is blank. */
  public String get(String field) {
    layout.field(field); // fails loudly on a misspelt field name
    return values.getOrDefault(field, "").strip();
  }

  /** Numeric field value; blank counts as zero. */
  public long number(String field) {
    String v = get(field);
    if (v.isEmpty()) {
      return 0;
    }
    try {
      return Long.parseLong(v);
    } catch (NumberFormatException e) {
      throw new NachaException(
          "Field "
              + layout.name()
              + "."
              + field
              + " is '"
              + v
              + "'"
              + (line > 0 ? " on line " + line : "")
              + " but must be digits only.");
    }
  }

  /** A copy with one field replaced. */
  public NachaRecord with(String field, String value) {
    layout.field(field);
    Map<String, String> copy = new LinkedHashMap<>(values);
    copy.put(field, value);
    return new NachaRecord(layout, type, copy, line);
  }

  /** The 94-character line. */
  public String toLine() {
    return layout.format(type, values);
  }
}
