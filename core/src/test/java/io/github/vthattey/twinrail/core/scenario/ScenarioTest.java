package io.github.vthattey.twinrail.core.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.core.TestMessages;
import io.github.vthattey.twinrail.core.definition.DefinitionException;
import io.github.vthattey.twinrail.core.matcher.XmlMessage;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ScenarioTest {
  public static final String SAMPLE =
      """
      format: 1
      scenarios:
        - id: closed-account
          description: Creditor account is closed
          match:
            message: pacs.008.001.08
            where:
              - { path: /Document/FIToFICstmrCdtTrf/CdtTrfTxInf/Cdtr/Nm, equals: "Closed Corp" }
          action: { type: reject, reason: AC04, text: Account closed }
        - id: slow-bank
          match:
            where:
              - { path: /Document/FIToFICstmrCdtTrf/CdtTrfTxInf/Cdtr/Nm, equals: "Slow Corp" }
          action: { type: delay, duration: PT30S }
        - id: black-hole
          match:
            where:
              - { path: /Document/FIToFICstmrCdtTrf/CdtTrfTxInf/IntrBkSttlmAmt, min: "1000" }
          action: { type: drop }
        - id: catch-all
          action: { type: reject, reason: NARR }
      """;

  private static ScenarioSet set() {
    return ScenarioLoader.load(SAMPLE, "s.yaml");
  }

  private static XmlMessage withCreditor(String name) {
    return TestMessages.pacs008With("Test Creditor", name);
  }

  private static String error(String yaml) {
    return assertThrows(DefinitionException.class, () -> ScenarioLoader.load(yaml, "s.yaml"))
        .getMessage();
  }

  @Test
  void loadsAllActionTypes() {
    var scenarios = set().scenarios();
    assertEquals(4, scenarios.size());
    assertEquals(new Action.Reject("AC04", "Account closed"), scenarios.get(0).action());
    assertEquals(new Action.Delay(Duration.ofSeconds(30)), scenarios.get(1).action());
    assertEquals(new Action.Drop(), scenarios.get(2).action());
    assertEquals(new Action.Reject("NARR", "Rejected by scenario"), scenarios.get(3).action());
  }

  @Test
  void firstMatchWins() {
    assertEquals(
        "closed-account", set().firstMatch(withCreditor("Closed Corp")).orElseThrow().id());
    assertEquals("slow-bank", set().firstMatch(withCreditor("Slow Corp")).orElseThrow().id());
  }

  @Test
  void aScenarioWithNoMatchBlockMatchesEverythingSoOrderMatters() {
    assertEquals("catch-all", set().firstMatch(withCreditor("Anyone")).orElseThrow().id());
  }

  @Test
  void numericMatchersWorkInScenarios() {
    XmlMessage big = TestMessages.pacs008With(">125.50<", ">5000.00<");
    assertEquals("black-hole", set().firstMatch(big).orElseThrow().id());
  }

  @Test
  void messageTypeFiltersApply() {
    XmlMessage other =
        XmlMessage.parse(
            "pacs.002.001.10",
            "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10\"/>");
    // closed-account is pacs.008-only and must not match; the catch-all has no filter.
    assertEquals("catch-all", set().firstMatch(other).orElseThrow().id());
  }

  @Test
  void aMissingElementNeverMatchesASelector() {
    // No creditor name at all: "creditor equals Slow Corp" must not select it.
    String noCreditorName =
        TestMessages.resource("/codec/pacs008-input.xml").replace("<Nm>Test Creditor</Nm>", "");
    XmlMessage message = TestMessages.pacs008(noCreditorName);
    assertEquals("catch-all", set().firstMatch(message).orElseThrow().id());
  }

  @Test
  void absentSelectsOnAMissingElement() {
    String yaml =
        "format: 1\nscenarios:\n  - id: no-name\n    match:\n      where:\n"
            + "        - { path: /Document/FIToFICstmrCdtTrf/CdtTrfTxInf/Cdtr/Nm, absent: true }\n"
            + "    action: { type: drop }\n";
    ScenarioSet absent = ScenarioLoader.load(yaml, "a.yaml");
    String noCreditorName =
        TestMessages.resource("/codec/pacs008-input.xml").replace("<Nm>Test Creditor</Nm>", "");
    assertEquals(
        "no-name", absent.firstMatch(TestMessages.pacs008(noCreditorName)).orElseThrow().id());
    assertEquals(Optional.empty(), absent.firstMatch(TestMessages.pacs008()));
  }

  @Test
  void anEmptySetMatchesNothing() {
    assertEquals(Optional.empty(), ScenarioSet.empty().firstMatch(TestMessages.pacs008()));
  }

  @Test
  void errorsAreSpecificAndActionable() {
    assertTrue(
        error("format: 1\nscenarios:\n  - id: a\n    action: {type: bounce}\n")
            .contains("unknown action type 'bounce'"));
    assertTrue(
        error("format: 1\nscenarios:\n  - id: a\n    action: {type: delay, duration: soon}\n")
            .contains("ISO 8601 duration"));
    assertTrue(
        error("format: 1\nscenarios:\n  - id: a\n    action: {type: delay, duration: PT0S}\n")
            .contains("greater than zero"));
    assertTrue(
        error("format: 1\nscenarios:\n  - id: a\n    action: {type: reject, reason: nope}\n")
            .contains("not a reason code"));
    assertTrue(
        error("format: 1\nscenarios:\n  - id: a\n    colour: red\n    action: {type: drop}\n")
            .contains("unknown field 'colour'"));
    assertTrue(
        error(
                "format: 1\nscenarios:\n  - id: a\n    action: {type: drop}\n  - id: a\n    action: {type: drop}\n")
            .contains("two scenarios with the id a"));
    assertTrue(error("scenarios: []\n").contains("format: 1"));
    assertTrue(
        error("format: 1\nscenarios:\n  - id: a\n    action: {type: drop, extra: 1}\n")
            .contains("unknown field 'extra'"));
  }
}
