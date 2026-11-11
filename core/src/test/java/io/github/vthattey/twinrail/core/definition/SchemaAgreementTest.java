package io.github.vthattey.twinrail.core.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.github.vthattey.twinrail.core.TestMessages;
import io.github.vthattey.twinrail.core.rules.RulePackLoader;
import io.github.vthattey.twinrail.core.scenario.ScenarioLoader;
import io.github.vthattey.twinrail.core.scenario.ScenarioTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The published JSON Schemas and the Java loaders must give the same verdict. The loaders are
 * stricter in a few ways a schema cannot express (duplicate ids, regex syntax); those cases are
 * excluded from the corpus.
 */
class SchemaAgreementTest {
  private static final SchemaRegistry REGISTRY =
      SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

  private static Schema schema(String file) {
    return REGISTRY.getSchema(TestMessages.resource("/schemas/" + file));
  }

  private static final Schema RULE_PACK = schema("rule-pack-1.schema.json");
  private static final Schema SCENARIO = schema("scenario-1.schema.json");

  private static boolean schemaAccepts(Schema schema, String yaml) {
    return schema.validate(yaml, InputFormat.YAML).isEmpty();
  }

  private static boolean loaderAccepts(Consumer<String> loader, String yaml) {
    try {
      loader.accept(yaml);
      return true;
    } catch (DefinitionException e) {
      return false;
    }
  }

  private static final String PACK_HEAD =
      "format: 1\npack: {id: t, name: T, version: \"1\"}\nrules:\n";
  private static final String RULE =
      "  - id: T-1\n    message: pacs.008.001.08\n    source: {spec: s, version: \"1\"}\n"
          + "    assert: {path: /Document/A/B, equals: \"x\"}\n    reason: FF01\n    text: t\n";

  @Test
  void rulePackCorpus() {
    List<String> valid =
        List.of(
            TestMessages.resource("/packs/example.yaml"),
            PACK_HEAD + RULE,
            PACK_HEAD + RULE.replace("equals: \"x\"", "oneOf: [\"a\", \"b\"]"),
            PACK_HEAD + RULE.replace("equals: \"x\"", "min: 5"),
            PACK_HEAD + RULE.replace("equals: \"x\"", "min: \"0.01\""),
            PACK_HEAD + RULE.replace("equals: \"x\"", "present: true"),
            PACK_HEAD + RULE.replace("/Document/A/B", "\"/Document/A[2]/@Ccy\""),
            PACK_HEAD + RULE + "    severity: warning\n");
    List<String> invalid =
        List.of(
            PACK_HEAD.replace("format: 1\n", "") + RULE,
            PACK_HEAD.replace("format: 1", "format: 2") + RULE,
            PACK_HEAD + RULE.replace("    source: {spec: s, version: \"1\"}\n", ""),
            PACK_HEAD + RULE.replace(", version: \"1\"", ""),
            PACK_HEAD + RULE.replace("equals: \"x\"", "equals: 1"),
            PACK_HEAD + RULE.replace("equals: \"x\"", "equals: \"x\", present: true"),
            PACK_HEAD + RULE.replace("{path: /Document/A/B, equals: \"x\"}", "{equals: \"x\"}"),
            PACK_HEAD + RULE.replace("/Document/A/B", "//A"),
            PACK_HEAD + RULE.replace("/Document/A/B", "\"/Document/A[0]\""),
            PACK_HEAD + RULE.replace("/Document/A/B", "/Document/text()"),
            PACK_HEAD + RULE.replace("reason: FF01", "reason: bad"),
            PACK_HEAD + RULE.replace("pacs.008.001.08", "pacs008"),
            PACK_HEAD + RULE + "    sevrity: warning\n",
            PACK_HEAD + RULE + "    severity: fatal\n",
            PACK_HEAD.replace("version: \"1\"", "version: 1") + RULE,
            "format: 1\npack: {id: t, name: T, version: \"1\"}\nrules: []\n");
    for (String yaml : valid) {
      assertTrue(schemaAccepts(RULE_PACK, yaml), "schema should accept:\n" + yaml);
      assertTrue(
          loaderAccepts(y -> RulePackLoader.load(y, "t.yaml"), yaml),
          "loader should accept:\n" + yaml);
    }
    for (String yaml : invalid) {
      assertEquals(false, schemaAccepts(RULE_PACK, yaml), "schema should reject:\n" + yaml);
      assertEquals(
          false,
          loaderAccepts(y -> RulePackLoader.load(y, "t.yaml"), yaml),
          "loader should reject:\n" + yaml);
    }
  }

  private static final String SC_HEAD = "format: 1\nscenarios:\n";

  @Test
  void scenarioCorpus() {
    List<String> valid =
        List.of(
            ScenarioTest.SAMPLE,
            "format: 1\nscenarios: []\n",
            SC_HEAD + "  - id: a\n    action: {type: drop}\n",
            SC_HEAD + "  - id: a\n    action: {type: delay, duration: PT2M}\n",
            SC_HEAD + "  - id: a\n    action: {type: reject, reason: AC04}\n",
            SC_HEAD
                + "  - id: a\n    match: {message: nacha.entry}\n    action: {type: return, code: R01}\n",
            SC_HEAD
                + "  - id: a\n    action: {type: return, code: R03, after: PT0S, text: closed}\n",
            SC_HEAD + "  - id: a\n    action: {type: noc, code: C01, account: \"12345\"}\n",
            SC_HEAD
                + "  - id: a\n    action: {type: noc, code: C03, routing: \"021000021\", account: \"12345\"}\n",
            SC_HEAD + "  - id: a\n    action: {type: noc, code: C09, individualId: \"ID1\"}\n");
    List<String> invalid =
        List.of(
            "scenarios: []\n",
            SC_HEAD + "  - id: a\n",
            SC_HEAD + "  - id: a\n    action: {type: bounce}\n",
            SC_HEAD + "  - id: a\n    action: {type: return}\n",
            SC_HEAD + "  - id: a\n    action: {type: return, code: X01}\n",
            SC_HEAD + "  - id: a\n    action: {type: return, code: R01, after: soon}\n",
            SC_HEAD + "  - id: a\n    action: {type: noc, code: C01}\n",
            SC_HEAD
                + "  - id: a\n    action: {type: noc, code: C01, account: \"1\", routing: \"2\"}\n",
            SC_HEAD + "  - id: a\n    action: {type: noc, code: C13, account: \"1\"}\n",
            SC_HEAD + "  - id: a\n    action: {type: noc, code: R01, account: \"1\"}\n",
            SC_HEAD + "  - id: a\n    match: {message: nacha.file}\n    action: {type: drop}\n",
            SC_HEAD + "  - id: a\n    action: {type: reject}\n",
            SC_HEAD + "  - id: a\n    action: {type: reject, reason: nope}\n",
            SC_HEAD + "  - id: a\n    action: {type: delay}\n",
            SC_HEAD + "  - id: a\n    action: {type: delay, duration: soon}\n",
            SC_HEAD + "  - id: a\n    action: {type: drop, extra: 1}\n",
            SC_HEAD + "  - id: a\n    colour: red\n    action: {type: drop}\n",
            SC_HEAD + "  - id: a\n    match: {message: pacs008}\n    action: {type: drop}\n",
            SC_HEAD
                + "  - id: a\n    match: {where: [{equals: \"x\"}]}\n    action: {type: drop}\n");
    for (String yaml : valid) {
      assertTrue(schemaAccepts(SCENARIO, yaml), "schema should accept:\n" + yaml);
      assertTrue(
          loaderAccepts(y -> ScenarioLoader.load(y, "s.yaml"), yaml),
          "loader should accept:\n" + yaml);
    }
    for (String yaml : invalid) {
      assertEquals(false, schemaAccepts(SCENARIO, yaml), "schema should reject:\n" + yaml);
      assertEquals(
          false,
          loaderAccepts(y -> ScenarioLoader.load(y, "s.yaml"), yaml),
          "loader should reject:\n" + yaml);
    }
  }

  /** The linter: every pack shipped in the rule-packs module loads and matches the schema. */
  @Test
  void everyShippedRulePackIsValid() throws IOException {
    Path dir = Path.of(System.getProperty("rulePacksDir"));
    try (Stream<Path> files = Files.list(dir)) {
      List<Path> packs = files.filter(p -> p.toString().endsWith(".yaml")).sorted().toList();
      assertTrue(!packs.isEmpty(), "no rule packs found in " + dir);
      for (Path pack : packs) {
        String yaml = Files.readString(pack);
        assertTrue(schemaAccepts(RULE_PACK, yaml), pack + " does not match the schema");
        RulePackLoader.load(yaml, pack.getFileName().toString());
      }
    }
  }
}
