package io.github.vthattey.twinrail.core.matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vthattey.twinrail.core.TestMessages;
import io.github.vthattey.twinrail.core.definition.DefinitionException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PathExprTest {
  private static List<String> values(String path) {
    return TestMessages.pacs008().values(PathExpr.parse(path));
  }

  @Test
  void selectsElementsIgnoringNamespacePrefixes() {
    assertEquals(List.of("TWIN-000001"), values("/Document/FIToFICstmrCdtTrf/GrpHdr/MsgId"));
  }

  @Test
  void selectsAttributes() {
    assertEquals(
        List.of("CAD"), values("/Document/FIToFICstmrCdtTrf/CdtTrfTxInf/IntrBkSttlmAmt/@Ccy"));
  }

  @Test
  void selectsByPositionAndWildcard() {
    assertEquals(
        List.of("Test Debtor"), values("/Document/FIToFICstmrCdtTrf/CdtTrfTxInf[1]/Dbtr/Nm"));
    assertEquals(List.of(), values("/Document/FIToFICstmrCdtTrf/CdtTrfTxInf[2]/Dbtr/Nm"));
    assertEquals(List.of("1"), values("/Document/*/GrpHdr/NbOfTxs"));
  }

  @Test
  void aMissingElementSelectsNothing() {
    assertEquals(List.of(), values("/Document/FIToFICstmrCdtTrf/GrpHdr/NoSuchThing"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Document/GrpHdr", // not absolute
        "//GrpHdr", // descendant search
        "/Document/", // trailing slash
        "/Document//GrpHdr",
        "/Document/GrpHdr[0]", // 1-based
        "/Document/@Ccy/Amt", // attribute not last
        "/Document/text()", // function
        "/Document/*[local-name()='x']", // arbitrary predicate
        "/Document/GrpHdr | /Other", // union
        "/Document/..", // parent axis
        ""
      })
  void rejectsAnythingOutsideTheRestrictedSyntax(String path) {
    DefinitionException e = assertThrows(DefinitionException.class, () -> PathExpr.parse(path));
    assertTrue(e.getMessage().contains("Example:"), "tells the author what a good path looks like");
  }
}
