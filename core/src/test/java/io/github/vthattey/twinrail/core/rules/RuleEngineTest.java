package io.github.vthattey.twinrail.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.core.TestMessages;
import io.github.vthattey.twinrail.core.matcher.XmlMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleEngineTest {
  private static final RulePack EXAMPLE =
      RulePackLoader.load(TestMessages.resource("/packs/example.yaml"), "example.yaml");
  private final RuleEngine engine = new RuleEngine();

  private Evaluation check(XmlMessage message) {
    return engine.evaluate(EXAMPLE, message);
  }

  @Test
  void aValidMessagePasses() {
    Evaluation result = check(TestMessages.pacs008());
    assertEquals(List.of(), result.violations());
    assertFalse(result.rejected());
  }

  @Test
  void aRejectionNamesTheRuleTheReasonCodeAndTheSource() {
    Evaluation result = check(TestMessages.pacs008With("<NbOfTxs>1<", "<NbOfTxs>2<"));
    assertTrue(result.rejected());
    Violation v = result.firstError().orElseThrow();
    assertEquals("EX-001", v.ruleId());
    assertEquals("FF01", v.reason());
    assertEquals("/Document/FIToFICstmrCdtTrf/GrpHdr/NbOfTxs", v.path());
    assertEquals("equals '1'", v.expectation());
    assertEquals("Twinrail example pack", v.source().spec());
    assertEquals(
        "Rule EX-001 (reason FF01): A credit transfer message must carry exactly one transaction.",
        v.summary());
  }

  @Test
  void numericBoundsUseDecimalComparison() {
    Violation v = check(TestMessages.pacs008With(">125.50<", ">0.00<")).firstError().orElseThrow();
    assertEquals("EX-002", v.ruleId());
    assertEquals("AM01", v.reason());
    assertEquals("is at least 0.01", v.expectation());
    assertFalse(check(TestMessages.pacs008With(">125.50<", ">0.01<")).rejected());
  }

  @Test
  void attributesAreCheckedToo() {
    Violation v =
        check(TestMessages.pacs008With("Ccy=\"CAD\"", "Ccy=\"cad\"")).firstError().orElseThrow();
    assertEquals("EX-003", v.ruleId());
    assertEquals("AM03", v.reason());
  }

  @Test
  void whenGuardsALimitedRule() {
    // Applies (member id present) and fails: nine digits required.
    Violation v = check(TestMessages.pacs008With("000000002", "123")).firstError().orElseThrow();
    assertEquals("EX-005", v.ruleId());
    assertEquals("RC01", v.reason());
  }

  @Test
  void anAssertionOnAMissingElementHoldsUnlessPresenceIsRequired() {
    // EX-004 limits EndToEndId length; with no EndToEndId it holds. A when guard on a missing
    // element does not select, so EX-005 is skipped rather than failing.
    String noAgentId =
        TestMessages.resource("/codec/pacs008-input.xml")
            .replace("<ClrSysMmbId><MmbId>000000002</MmbId></ClrSysMmbId>", "")
            .replaceAll("<EndToEndId>[^<]*</EndToEndId>", "");
    assertEquals(List.of(), check(TestMessages.pacs008(noAgentId)).violations());
  }

  @Test
  void whenFalseSkipsTheRule() {
    String yaml =
        "format: 1\npack: {id: t, name: T, version: \"1\"}\nrules:\n"
            + "  - id: G-1\n    message: pacs.008.001.08\n"
            + "    source: {spec: s, version: \"1\"}\n"
            + "    when: [{path: /Document/FIToFICstmrCdtTrf/CdtTrfTxInf/Dbtr/Nm, equals: \"Other\"}]\n"
            + "    assert: {path: /Document/FIToFICstmrCdtTrf/GrpHdr/MsgId, equals: \"never\"}\n"
            + "    reason: FF01\n    text: t\n";
    RulePack pack = RulePackLoader.load(yaml, "g.yaml");
    assertFalse(engine.evaluate(pack, TestMessages.pacs008()).rejected());
  }

  @Test
  void warningsAreReportedButDoNotReject() {
    String withoutUetr =
        TestMessages.resource("/codec/pacs008-input.xml").replaceAll("<UETR>[^<]*</UETR>", "");
    Evaluation result = check(TestMessages.pacs008(withoutUetr));
    assertFalse(result.rejected());
    assertEquals(1, result.violations().size());
    assertEquals("EX-006", result.violations().get(0).ruleId());
    assertEquals(Severity.WARNING, result.violations().get(0).severity());
  }

  @Test
  void violationsAreReportedInPackOrderAndTheFirstErrorIsTheRejection() {
    Evaluation result =
        check(
            TestMessages.pacs008(
                TestMessages.resource("/codec/pacs008-input.xml")
                    .replace(">125.50<", ">0.00<")
                    .replace("<NbOfTxs>1<", "<NbOfTxs>2<")));
    assertEquals(
        List.of("EX-001", "EX-002"), result.violations().stream().map(Violation::ruleId).toList());
    assertEquals("EX-001", result.firstError().orElseThrow().ruleId());
  }

  @Test
  void rulesForOtherMessageTypesDoNotApply() {
    XmlMessage other =
        XmlMessage.parse(
            "pacs.002.001.10",
            "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10\"/>");
    assertFalse(check(other).rejected());
  }
}
