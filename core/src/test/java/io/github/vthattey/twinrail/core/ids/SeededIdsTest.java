package io.github.vthattey.twinrail.core.ids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SeededIdsTest {
  @Test
  void sameSeedGivesSameSequence() {
    SeededIds a = new SeededIds(42);
    SeededIds b = new SeededIds(42);
    for (int i = 0; i < 100; i++) {
      assertEquals(a.uuid(), b.uuid());
      assertEquals(a.sequence("TWIN"), b.sequence("TWIN"));
    }
  }

  @Test
  void differentSeedsDiffer() {
    assertNotEquals(new SeededIds(1).uuid(), new SeededIds(2).uuid());
  }

  @Test
  void uuidsAreVersion4Variant2() {
    UUID uuid = new SeededIds(7).uuid();
    assertEquals(4, uuid.version());
    assertEquals(2, uuid.variant());
  }

  @Test
  void sequenceFormat() {
    SeededIds ids = new SeededIds(0);
    assertEquals("TWIN-000001", ids.sequence("TWIN"));
    assertEquals("PMT-000002", ids.sequence("PMT"));
  }

  @Test
  void goldenValueIsStableAcrossJvms() {
    // Cross-checked against an independent SplitMix64 implementation. If this changes, recorded
    // sessions would no longer replay.
    assertEquals("bdd73226-2feb-4e95-a8ef-e333b266f103", new SeededIds(42).uuid().toString());
  }
}
