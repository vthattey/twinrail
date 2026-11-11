package io.github.vthattey.twinrail.core.nacha;

/** The file is not a readable ACH file, or a record could not be written. Says what to change. */
public final class NachaException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public NachaException(String message) {
    super(message);
  }
}
