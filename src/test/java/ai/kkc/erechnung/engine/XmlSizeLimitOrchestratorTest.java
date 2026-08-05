package ai.kkc.erechnung.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.kkc.erechnung.model.Verdict;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XmlSizeLimitOrchestratorTest {

  @TempDir Path tmp;

  @Test
  void rejectsStandaloneXmlOverLimitWithoutRunningEngines() throws Exception {
    Path xml = tmp.resolve("big.xml");
    // Minimal well-formed-looking payload larger than 200 bytes
    String body = "<Invoice>" + "x".repeat(250) + "</Invoice>";
    Files.writeString(xml, body, StandardCharsets.UTF_8);

    ValidationOrchestrator orch =
        new ValidationOrchestrator(
            new MustangEngine(new FormatDetector()),
            new KositEngine(),
            new XmlExtractor(),
            new ai.kkc.erechnung.policy.ConformancePolicy(),
            200);

    var report = orch.validate(xml);
    assertEquals(Verdict.TOOL_ERROR, report.getVerdict());
    assertTrue(report.getErechnungXml().isEmpty());
    assertTrue(report.getMustangPruefbericht().isEmpty());
    assertEquals(200L, report.getSummary().get("max_xml_bytes"));
    assertTrue(report.getSummary().get("error").toString().contains("exceeds size limit"));
    assertEquals(3, orch.exitCode(report));
  }
}
