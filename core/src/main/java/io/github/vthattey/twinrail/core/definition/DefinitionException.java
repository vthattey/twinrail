package io.github.vthattey.twinrail.core.definition;

/**
 * A rule pack or scenario file is invalid. The message names the file and the item, says what is
 * wrong and what to change.
 */
public final class DefinitionException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public DefinitionException(String message) {
    super(message);
  }

  public DefinitionException(String message, Throwable cause) {
    super(message, cause);
  }
}
