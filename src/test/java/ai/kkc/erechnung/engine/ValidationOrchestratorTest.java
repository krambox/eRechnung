package ai.kkc.erechnung.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.kkc.erechnung.json.ReportJson;
import ai.kkc.erechnung.model.Verdict;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ValidationOrchestratorTest {

  private static ValidationOrchestrator orchestrator;
  private static Path fixtures;
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ReportJson JSON = new ReportJson();

  @BeforeAll
  static void setUp() {
    orchestrator = ValidationOrchestrator.createDefault();
    fixtures = Path.of("src/test/resources/fixtures");
  }

  @Test
  void knownGoodXRechnungIsConformant() throws Exception {
    var report = orchestrator.validate(fixtures.resolve("good-xr-ubl.xml"));
    assertEquals(Verdict.CONFORMANT, report.getVerdict());
    assertEquals("xrechnung", report.getSummary().get("format"));
    assertTrue(report.getErechnungXml().contains("Invoice"));
    assertTrue((Boolean) ((java.util.Map<?, ?>) report.getSummary().get("engines")).get("kosit"));
    assertTrue(report.getMustangPruefbericht().contains("<validation"));
    JsonNode root = MAPPER.readTree(JSON.toJson(report));
    assertFalse(root.get("summary").get("sections").get("pdfa").get("applicable").asBoolean());
    assertTrue(root.has("erechnung"));
    assertTrue(root.has("mustang-pruefbericht"));
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
        report.getErrors().stream()
            .anyMatch(f -> "kosit".equals(f.engine()) || "mustang".equals(f.engine())));
  }
}
