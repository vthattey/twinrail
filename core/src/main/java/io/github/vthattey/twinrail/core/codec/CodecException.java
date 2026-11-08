package io.github.vthattey.twinrail.core.codec;

/** A message could not be read or written. The message says what went wrong and what to do next. */
public final class CodecException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public CodecException(String message) {
    super(message);
  }

  public CodecException(String message, Throwable cause) {
    super(message, cause);
  }
}
