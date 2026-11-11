package io.github.vthattey.twinrail.core.definition;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** Safe YAML loading: plain data only, no custom types, duplicate keys rejected, size limited. */
public final class Yamls {
  private static final int MAX_CODE_POINTS = 2_000_000;
  private static final int MAX_ALIASES = 10;

  private Yamls() {}

  /**
   * Parses YAML into maps, lists and scalars.
   *
   * @param name the file name used in error messages
   */
  public static Object load(String text, String name) {
    LoadSettings settings =
        LoadSettings.builder()
            .setLabel(name)
            .setAllowDuplicateKeys(false)
            .setMaxAliasesForCollections(MAX_ALIASES)
            .setCodePointLimit(MAX_CODE_POINTS)
            .build();
    try {
      return new Load(settings).loadFromString(text);
    } catch (RuntimeException e) {
      throw new DefinitionException(
          name
              + " is not valid YAML ("
              + e.getClass().getSimpleName()
              + "). Fix the syntax. Quote values that contain [ ] { } , or : or start with @, "
              + "for example path: \"/Document/A[2]/@Ccy\". Duplicate keys, custom tags and more than "
              + MAX_ALIASES
              + " aliases are not allowed.",
          e);
    }
  }
}
