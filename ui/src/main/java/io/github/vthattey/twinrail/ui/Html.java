package io.github.vthattey.twinrail.ui;

/**
 * The only way text reaches the page. Message contents (names, ids, whole XML documents) are
 * untrusted input, so every value goes through {@link #esc}; nothing in this module concatenates
 * raw text into markup (ADR 0006).
 */
final class Html {
  private Html() {}

  /** Escapes text for element content and for double- or single-quoted attribute values. */
  static String esc(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(text.length() + 16);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }
}
