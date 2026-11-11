package io.github.vthattey.twinrail.core.nacha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.core.definition.DefinitionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReasonCodesTest {
  static ReasonCodes shipped() throws IOException {
    Path file =
        Path.of(System.getProperty("rulePacksDir"))
            .resolveSibling("codes")
            .resolve("nacha-codes-1.yaml");
    return ReasonCodes.load(Files.readString(file), file.getFileName().toString());
  }

  @Test
  void shippedTableLoadsWithASourceOnEveryCode() throws IOException {
    ReasonCodes codes = shipped();
    assertEquals(40, codes.returnCodes().size());
    assertEquals(10, codes.changeCodes().size());
    for (String c : codes.returnCodes()) {
      assertNotNull(codes.returnCode(c).source().spec(), c);
      assertNotNull(codes.returnCode(c).timeframe(), c);
    }
    for (String c : codes.changeCodes()) {
      assertNotNull(codes.changeCode(c).source().version(), c);
    }
  }

  @Test
  void knownCodesHaveTheTitlesAndTimeFramesOfTheSource() throws IOException {
    ReasonCodes codes = shipped();
    assertEquals("Insufficient Funds", codes.returnCode("R01").title());
    assertEquals("2 Banking Days", codes.returnCode("R01").timeframe());
    assertEquals("60 Calendar Days", codes.returnCode("R07").timeframe());
    assertEquals("Incorrect DFI Account Number", codes.changeCode("C01").title());
  }

  @Test
  void codesTheSourceDoesNotListAreAbsentNotGuessed() throws IOException {
    ReasonCodes codes = shipped();
    assertNull(codes.changeCode("C04"));
    assertNull(codes.returnCode("R40"));
  }

  @Test
  void brokenTablesAreRefusedWithAnExplanation() {
    String head = "format: 1\ntable: {id: t, name: T, version: \"1\"}\n";
    String src = "source: {spec: s, version: v}";
    var dup =
        assertThrows(
            DefinitionException.class,
            () ->
                ReasonCodes.load(
                    head
                        + "returns:\n  - {code: R01, title: a, timeframe: b, "
                        + src
                        + "}\n  - {code: R01, title: a, timeframe: b, "
                        + src
                        + "}\nchanges: []\n",
                    "t.yaml"));
    assertTrue(dup.getMessage().contains("twice"));

    var badCode =
        assertThrows(
            DefinitionException.class,
            () ->
                ReasonCodes.load(
                    head
                        + "returns:\n  - {code: X1, title: a, timeframe: b, "
                        + src
                        + "}\nchanges: []\n",
                    "t.yaml"));
    assertTrue(badCode.getMessage().contains("not a valid code"));

    assertThrows(
        DefinitionException.class,
        () ->
            ReasonCodes.load(
                head + "returns:\n  - {code: R01, title: a, timeframe: b}\nchanges: []\n",
                "t.yaml"));
  }
}
