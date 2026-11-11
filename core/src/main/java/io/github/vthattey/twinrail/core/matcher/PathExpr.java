package io.github.vthattey.twinrail.core.matcher;

import io.github.vthattey.twinrail.core.definition.DefinitionException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A restricted, XPath-like element path such as {@code /Document/FIToFICstmrCdtTrf/GrpHdr/MsgId}.
 *
 * <p>Syntax: absolute, steps separated by {@code /}; a step is an element name or {@code *},
 * optionally followed by a 1-based index like {@code [2]}; the last step may be an attribute such
 * as {@code @Ccy}. Namespaces are ignored (names match by local name). Nothing else is allowed, so
 * a path can never run arbitrary XPath functions.
 */
public final class PathExpr {
  private static final String NAME = "[A-Za-z_][A-Za-z0-9_.-]*";
  private static final Pattern ELEMENT =
      Pattern.compile("(\\*|" + NAME + ")(?:\\[([1-9][0-9]{0,3})\\])?");
  private static final Pattern ATTRIBUTE = Pattern.compile("@(" + NAME + ")");

  private final String text;
  private final String xpath;

  private PathExpr(String text, String xpath) {
    this.text = text;
    this.xpath = xpath;
  }

  /**
   * @throws DefinitionException if the path is not valid
   */
  public static PathExpr parse(String path) {
    if (path == null || !path.startsWith("/") || path.startsWith("//") || path.endsWith("/")) {
      throw invalid(path, "it must be absolute, start with a single '/' and not end with '/'");
    }
    String[] steps = path.substring(1).split("/", -1);
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < steps.length; i++) {
      String step = steps[i];
      var attribute = ATTRIBUTE.matcher(step);
      var element = ELEMENT.matcher(step);
      if (attribute.matches()) {
        if (i != steps.length - 1) {
          throw invalid(path, "an attribute step can only be the last step");
        }
        parts.add("@*[local-name()='" + attribute.group(1) + "']");
      } else if (element.matches()) {
        String name = element.group(1);
        String base = name.equals("*") ? "*" : "*[local-name()='" + name + "']";
        parts.add(element.group(2) == null ? base : base + "[" + element.group(2) + "]");
      } else {
        throw invalid(path, "'" + step + "' is not an element name, '*' or '@attribute'");
      }
    }
    return new PathExpr(path, "/" + String.join("/", parts));
  }

  private static DefinitionException invalid(String path, String why) {
    return new DefinitionException(
        "Invalid path '"
            + path
            + "': "
            + why
            + ". Example: /Document/FIToFICstmrCdtTrf/GrpHdr/MsgId or "
            + "/Document/FIToFICstmrCdtTrf/CdtTrfTxInf[1]/IntrBkSttlmAmt/@Ccy.");
  }

  public String text() {
    return text;
  }

  String xpath() {
    return xpath;
  }

  @Override
  public String toString() {
    return text;
  }
}
