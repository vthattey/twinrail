package io.github.vthattey.twinrail.core.ids;

import java.util.Locale;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Deterministic id source: the same seed always yields the same sequence of ids, on any JVM. Not
 * for security; these are test identifiers.
 */
public final class SeededIds {
  private final SplittableRandom random;
  private long counter;

  public SeededIds(long seed) {
    this.random = new SplittableRandom(seed);
  }

  /** A version-4 style UUID (for example a UETR) drawn from the seeded stream. */
  public synchronized UUID uuid() {
    long msb = (random.nextLong() & 0xFFFFFFFFFFFF0FFFL) | 0x0000000000004000L;
    long lsb = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }

  /** The next id of the form {@code PREFIX-000001}. Counts across all prefixes. */
  public synchronized String sequence(String prefix) {
    return prefix + "-" + String.format(Locale.ROOT, "%06d", ++counter);
  }
}
