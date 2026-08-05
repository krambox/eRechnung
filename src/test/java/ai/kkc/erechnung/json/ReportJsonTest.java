package ai.kkc.erechnung.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.kkc.erechnung.model.Finding;
import ai.kkc.erechnung.model.ValidationReport;
import ai.kkc.erechnung.model.Verdict;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportJsonTest {

  private final ReportJson json = new ReportJson();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void emitsV03ContractShape() throws Exception {
    ValidationReport report = new ValidationReport();
    report.setVerdict(Verdict.NONCONFORMANT);
    report.setErechnungXml("<rsm:CrossIndustryInvoice/>");
    report.setMustangPruefbericht("<validation/>");
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("filename", "invoice.pdf");
    summary.put("duration_ms", 200L);
    summary.put("format", "facturx_en16931");
    summary.put("generation", "2");
    summary.put("profile", "urn:cen.eu:en16931:2017");
    summary.put("attachment", "factur-x.xml");
    summary.put("pdf_input", true);
    summary.put("validator", Map.of("mustang", "2.24.0"));
    summary.put("engines", Map.of("mustang", true, "kosit", false));
    report.setSummary(summary);
    report.addFinding(
        Finding.error(
            "mustang",
            "MUSTANG_11",
            "XMP Metadata: ConformanceLevel not found",
            "pdfa",
            null));
    report.addFinding(
        Finding.warning(
            "mustang",
            "MUSTANG_4",
            "[PEPPOL-EN16931-R008] empty",
            "schematron",
            "/*:SellerTradeParty/*:DefinedTradeContact[1]"));

    JsonNode root = mapper.readTree(json.toJson(report));

    assertEquals("nonconformant", root.get("verdict").asText());
    assertTrue(root.has("summary"));
    assertTrue(root.has("erechnung"));
    assertTrue(root.has("mustang-pruefbericht"));
    assertFalse(root.has("errors"));
    assertFalse(root.has("warnings"));
    assertFalse(root.has("notices"));
    assertFalse(root.has("metadata"));
    assertFalse(root.has("erechnung_xml"));

    JsonNode summaryNode = root.get("summary");
    assertEquals("invoice.pdf", summaryNode.get("filename").asText());
    assertEquals(200, summaryNode.get("duration_ms").asInt());
    assertEquals("error", summaryNode.get("sections").get("pdfa").get("status").asText());
    assertEquals("warning", summaryNode.get("sections").get("schematron").get("status").asText());
    assertEquals("ok", summaryNode.get("sections").get("schema").get("status").asText());
    assertEquals("MUSTANG_11", summaryNode.get("errors").get(0).get("id").asText());
    assertEquals("pdfa", summaryNode.get("errors").get(0).get("section").asText());
    assertEquals(
        "/*:SellerTradeParty/*:DefinedTradeContact[1]",
        summaryNode.get("warnings").get(0).get("location").asText());
    assertEquals("<rsm:CrossIndustryInvoice/>", root.get("erechnung").asText());
    assertEquals("<validation/>", root.get("mustang-pruefbericht").asText());
  }
}
