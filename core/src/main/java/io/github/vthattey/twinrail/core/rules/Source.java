package io.github.vthattey.twinrail.core.rules;

/**
 * Where a rule comes from: a public specification, its version and optionally the section.
 *
 * @param spec the specification name
 * @param version the specification version or date
 * @param section the section, table or page reference; may be {@code null}
 */
public record Source(String spec, String version, String section) {
  @Override
  public String toString() {
    return spec + " " + version + (section == null ? "" : ", " + section);
  }
}
