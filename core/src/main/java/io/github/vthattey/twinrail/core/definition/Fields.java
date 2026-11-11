package io.github.vthattey.twinrail.core.definition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Strict reader for one YAML mapping. Every key read is marked as used, and {@link #finish()}
 * rejects any key that was not read, so typos never pass silently.
 */
public final class Fields {
  private final Map<?, ?> map;
  private final String where;
  private final Set<String> used = new HashSet<>();

  private Fields(Map<?, ?> map, String where) {
    this.map = map;
    this.where = where;
  }

  /**
   * @param where describes the item for error messages, for example {@code "rule EX-001 in
   *     example.yaml"}
   */
  public static Fields of(Object node, String where) {
    if (!(node instanceof Map<?, ?> map)) {
      throw new DefinitionException(where + " must be a mapping of keys to values.");
    }
    for (Object key : map.keySet()) {
      if (!(key instanceof String)) {
        throw new DefinitionException(where + " has a key that is not text: " + key + ".");
      }
    }
    return new Fields(map, where);
  }

  public String where() {
    return where;
  }

  public boolean has(String key) {
    return map.containsKey(key);
  }

  /** Keys present in the mapping, in file order. */
  public Set<String> keys() {
    Set<String> keys = new LinkedHashSet<>();
    for (Object key : map.keySet()) {
      keys.add((String) key);
    }
    return keys;
  }

  public Object raw(String key) {
    used.add(key);
    return map.get(key);
  }

  public String requireString(String key) {
    return optionalString(key)
        .orElseThrow(() -> new DefinitionException(where + " is missing '" + key + "'."));
  }

  public Optional<String> optionalString(String key) {
    Object value = raw(key);
    if (value == null) {
      return Optional.empty();
    }
    if (!(value instanceof String text) || text.isBlank()) {
      throw new DefinitionException(
          where
              + ": '"
              + key
              + "' must be non-empty text. Put the value in quotes, for example "
              + key
              + ": \"1.50\".");
    }
    return Optional.of(text);
  }

  public List<Object> requireList(String key) {
    return optionalList(key)
        .orElseThrow(() -> new DefinitionException(where + " is missing '" + key + "'."));
  }

  public Optional<List<Object>> optionalList(String key) {
    Object value = raw(key);
    if (value == null) {
      return Optional.empty();
    }
    if (!(value instanceof List<?> list)) {
      throw new DefinitionException(where + ": '" + key + "' must be a list.");
    }
    return Optional.of(new ArrayList<Object>(list));
  }

  public Fields requireMap(String key) {
    return optionalMap(key)
        .orElseThrow(() -> new DefinitionException(where + " is missing '" + key + "'."));
  }

  public Optional<Fields> optionalMap(String key) {
    Object value = raw(key);
    if (value == null) {
      return Optional.empty();
    }
    return Optional.of(of(value, where + ", '" + key + "'"));
  }

  /** Fails if the mapping contains a key that was never read. */
  public void finish() {
    for (String key : keys()) {
      if (!used.contains(key)) {
        throw new DefinitionException(
            where + " has an unknown field '" + key + "'. Check the spelling against the docs.");
      }
    }
  }
}
