package ai.kkc.erechnung.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.kkc.erechnung.model.Verdict;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CorpusExpectationTest {

  private final Path root = Path.of("/repo/corpus");

  @Test
  void failPathExpectsReject() {
    var e =
        CorpusExpectation.forPath(
                root, root.resolve("ZUGFeRDv2/fail/Mustangproject/wrongFilename.pdf"))
            .orElseThrow();
    assertEquals(Verdict.NONCONFORMANT, e.verdict());
    assertTrue(e.rejectOnly());
    assertTrue(CorpusExpectation.matches(e, Verdict.NONCONFORMANT));
    assertTrue(CorpusExpectation.matches(e, Verdict.NOT_ERECHNUNG));
    assertTrue(!CorpusExpectation.matches(e, Verdict.CONFORMANT));
  }

  @Test
  void correctEn16931ExpectsConformant() {
    var e =
        CorpusExpectation.forPath(
                root,
                root.resolve(
                    "ZUGFeRDv2/correct/intarsys/EN16931/zugferd_2p0_EN16931_Einfach.pdf"))
            .orElseThrow();
    assertEquals(Verdict.CONFORMANT, e.verdict());
  }

  @Test
  void correctBasicEn16931ExpectsConformant() {
    var e =
        CorpusExpectation.forPath(
                root, root.resolve("ZUGFeRDv2/correct/intarsys/BASIC/zugferd_2p0_BASIC_Einfach.pdf"))
            .orElseThrow();
    assertEquals(Verdict.CONFORMANT, e.verdict());
  }

  @Test
  void zugferdV1IsSkipped() {
    var e =
        CorpusExpectation.forPath(
                root, root.resolve("ZUGFeRDv1/correct/Intarsys/ZUGFeRD_1p0_COMFORT_Einfach.pdf"))
            .orElseThrow();
    assertEquals(CorpusExpectation.Kind.SKIP, e.kind());
  }

  @Test
  void correctMinimumExpectsNotErechnung() {
    var e =
        CorpusExpectation.forPath(
                root, root.resolve("ZUGFeRDv2/correct/intarsys/MINIMUM/zugferd_2p0_MINIMUM.pdf"))
            .orElseThrow();
    assertEquals(Verdict.NOT_ERECHNUNG, e.verdict());
  }

  @Test
  void xmlRechnungExpectsConformant() {
    var e =
        CorpusExpectation.forPath(
                root, root.resolve("XML-Rechnung/FX/EN16931_Einfach.pdf"))
            .orElseThrow();
    assertEquals(Verdict.CONFORMANT, e.verdict());
  }

  @Test
  void unstructuredSkipped() {
    var e =
        CorpusExpectation.forPath(
                root, root.resolve("unstructured/some.pdf"))
            .orElseThrow();
    assertEquals(CorpusExpectation.Kind.SKIP, e.kind());
  }
}
