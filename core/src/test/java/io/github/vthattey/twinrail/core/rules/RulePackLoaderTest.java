package io.github.vthattey.twinrail.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.core.TestMessages;
import io.github.vthattey.twinrail.core.definition.DefinitionException;
import java.util.List;
import org.junit.jupiter.api.Test;

class RulePackLoaderTest {
  private static final String HEADER =
      "format: 1\npack: {id: t, name: T, version: \"1\"}\nrules:\n";

  private static String rule(String extra) {
    return "  - id: T-1\n"
        + "    message: pacs.008.001.08\n"
        + "    source: {spec: \"Some public spec\", version: \"2024\"}\n"
        + "    assert: {path: /Document/FIToFICstmrCdtTrf/GrpHdr/NbOfTxs, equals: \"1\"}\n"
        + "    reason: FF01\n"
        + "    text: Must be one.\n"
        + extra;
  }

  private static String error(String yaml) {
    return assertThrows(DefinitionException.class, () -> RulePackLoader.load(yaml, "t.yaml"))
        .getMessage();
  }

  @Test
  void loadsTheExamplePack() {
    RulePack pack =
        RulePackLoader.load(TestMessages.resource("/packs/example.yaml"), "example.yaml");
    assertEquals("example", pack.id());
    assertEquals("0.1", pack.version());
    assertEquals(6, pack.rules().size());
    Rule last = pack.rules().get(5);
    assertEquals(Severity.WARNING, last.severity());
    assertEquals("Twinrail example pack", last.source().spec());
    assertEquals(
        List.of("EX-001", "EX-002", "EX-003", "EX-004", "EX-005", "EX-006"),
        pack.rulesFor("pacs.008.001.08").stream().map(Rule::id).toList());
    assertEquals(List.of(), pack.rulesFor("pacs.002.001.10"));
  }

  @Test
  void aMinimalPackLoads() {
    assertEquals(1, RulePackLoader.load(HEADER + rule(""), "t.yaml").rules().size());
  }

  @Test
  void everyRuleMustRecordItsSource() {
    String yaml =
        (HEADER + rule(""))
            .replace("    source: {spec: \"Some public spec\", version: \"2024\"}\n", "");
    String message = error(yaml);
    assertTrue(message.contains("Rule T-1 in t.yaml has no 'source'"), message);
    assertTrue(message.contains("public specification"), message);
  }

  @Test
  void sourceNeedsSpecAndVersion() {
    assertTrue(
        error((HEADER + rule("")).replace(", version: \"2024\"", ""))
            .contains("missing 'version'"));
  }

  @Test
  void unknownKeysAreErrorsNotSilentlyIgnored() {
    String message = error(HEADER + rule("    sevrity: warning\n"));
    assertTrue(message.contains("unknown field 'sevrity'"), message);
    assertTrue(message.contains("Rule T-1"), message);
  }

  @Test
  void formatMustBeDeclared() {
    assertTrue(error(HEADER.replace("format: 1\n", "") + rule("")).contains("format: 1"));
    assertTrue(error(HEADER.replace("format: 1", "format: 2") + rule("")).contains("found 2"));
  }

  @Test
  void duplicateRuleIdsAreRejected() {
    assertTrue(error(HEADER + rule("") + rule("")).contains("two rules with the id T-1"));
  }

  @Test
  void duplicateYamlKeysAreRejected() {
    assertTrue(error(HEADER + rule("    reason: AM01\n")).contains("not valid YAML"));
  }

  @Test
  void badPathNamesTheRuleAndShowsAnExample() {
    String message =
        error(
            (HEADER + rule("")).replace("/Document/FIToFICstmrCdtTrf/GrpHdr/NbOfTxs", "//NbOfTxs"));
    assertTrue(message.contains("Rule T-1"), message);
    assertTrue(message.contains("Example:"), message);
  }

  @Test
  void unquotedNumbersInEqualsAreRefusedBecauseTheyLoseTrailingZeros() {
    String message = error((HEADER + rule("")).replace("equals: \"1\"", "equals: 1.50"));
    assertTrue(message.contains("quoted text"), message);
  }

  @Test
  void aRuleNeedsExactlyOneCondition() {
    assertTrue(
        error((HEADER + rule("")).replace("equals: \"1\"}", "equals: \"1\", present: true}"))
            .contains("exactly one condition"));
    assertTrue(
        error((HEADER + rule("")).replace(", equals: \"1\"}", "}"))
            .contains("exactly one condition"));
  }

  @Test
  void reasonAndMessageIdentifiersAreChecked() {
    assertTrue(
        error((HEADER + rule("")).replace("reason: FF01", "reason: bad"))
            .contains("not a reason code"));
    assertTrue(
        error((HEADER + rule("")).replace("pacs.008.001.08", "pacs008"))
            .contains("not a message identifier"));
  }

  @Test
  void invalidRegexIsReportedWithTheRule() {
    String message = error((HEADER + rule("")).replace("equals: \"1\"", "matches: \"[\""));
    assertTrue(message.contains("not a valid regular expression"), message);
    assertTrue(message.contains("Rule T-1"), message);
  }

  @Test
  void anEmptyPackIsRejected() {
    assertTrue(
        error("format: 1\npack: {id: t, name: T, version: \"1\"}\nrules: []\n")
            .contains("no rules"));
  }

  @Test
  void customYamlTagsAreNotConstructed() {
    String yaml =
        HEADER.replace("name: T", "name: !!java.net.URL \"http://example.invalid\"") + rule("");
    assertThrows(DefinitionException.class, () -> RulePackLoader.load(yaml, "t.yaml"));
  }
}
