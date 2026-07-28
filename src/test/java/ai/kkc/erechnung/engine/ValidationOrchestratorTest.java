package ai.kkc.erechnung.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.kkc.erechnung.model.Verdict;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ValidationOrchestratorTest {

  private static ValidationOrchestrator orchestrator;
  private static Path fixtures;

  @BeforeAll
  static void setUp() {
    orchestrator = ValidationOrchestrator.createDefault();
    fixtures = Path.of("src/test/resources/fixtures");
  }

  @Test
  void knownGoodXRechnungIsConformant() throws Exception {
    var report = orchestrator.validate(fixtures.resolve("good-xr-ubl.xml"));
    assertEquals(Verdict.CONFORMANT, report.getVerdict());
    assertEquals("xrechnung", report.getMetadata().get("format"));
    assertTrue(report.getErechnungXml().contains("Invoice"));
    assertTrue((Boolean) ((java.util.Map<?, ?>) report.getMetadata().get("engines")).get("kosit"));
  }

  @Test
  void unrelatedXmlIsNotErechnung() throws Exception {
    var report = orchestrator.validate(fixtures.resolve("not-invoice.xml"));
    assertEquals(Verdict.NOT_ERECHNUNG, report.getVerdict());
    assertEquals(2, orchestrator.exitCode(report));
  }

  @Test
  void imageOnlyPdfIsNotErechnung() throws Exception {
    var report = orchestrator.validate(fixtures.resolve("not-erechnung.pdf"));
    assertEquals(Verdict.NOT_ERECHNUNG, report.getVerdict());
    assertTrue(report.getErechnungXml().isEmpty() || report.getErechnungXml().isBlank());
  }

  @Test
  void badXRechnungRunsKositAndIsNonconformant() throws Exception {
    var report = orchestrator.validate(fixtures.resolve("bad-xr-ubl.xml"));
    assertEquals(Verdict.NONCONFORMANT, report.getVerdict());
    assertFalse(report.getErrors().isEmpty());
    assertTrue(
        report.getErrors().stream().anyMatch(f -> "kosit".equals(f.engine()) || "mustang".equals(f.engine())));
  }
}
