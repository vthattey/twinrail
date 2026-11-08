package io.github.vthattey.twinrail.core.recorder;

/** Which way a recorded message travelled, from the emulator's point of view. */
public enum Direction {
  /** Received by the emulator (for example a {@code pacs.008} from the system under test). */
  INBOUND,
  /** Sent by the emulator (for example a {@code pacs.002}). */
  OUTBOUND
}
