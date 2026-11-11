package io.github.vthattey.twinrail.testcontainers;

/**
 * The emulator refused a request, or could not be reached. For a refusal the message is the
 * emulator's own "what went wrong and what to do next" text.
 */
public final class TwinrailException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private final int status;

  /**
   * @param status the HTTP status, or -1 if the emulator could not be reached
   */
  public TwinrailException(int status, String message) {
    super(message);
    this.status = status;
  }

  public TwinrailException(String message, Throwable cause) {
    super(message, cause);
    this.status = -1;
  }

  /** The HTTP status of the refusal, or -1 if the emulator could not be reached. */
  public int status() {
    return status;
  }
}
