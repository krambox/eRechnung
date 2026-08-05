package ai.kkc.erechnung.json;

import ai.kkc.erechnung.model.CheckSection;
import ai.kkc.erechnung.model.Finding;
import ai.kkc.erechnung.model.Severity;
import ai.kkc.erechnung.model.ValidationReport;
import ai.kkc.erechnung.model.Verdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Serializes {@link ValidationReport} to the 0.3 CLI JSON contract (stdout only). */
public final class ReportJson {

  private final ObjectMapper mapper;

  public ReportJson() {
    this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  }

  public String toJson(ValidationReport report) {
    try {
      ObjectNode root = mapper.createObjectNode();
      root.put("verdict", jsonVerdict(report.getVerdict()));
      ObjectNode summary = mapper.valueToTree(report.getSummary());
      if (summary == null || summary.isNull()) {
        summary = mapper.createObjectNode();
      }
      boolean pdfInput = Boolean.TRUE.equals(report.getSummary().get("pdf_input"));
      summary.remove("pdf_input");
      summary.set("sections", buildSections(report, pdfInput));
      summary.set("errors", findingsArray(report.getErrors()));
      summary.set("warnings", findingsArray(report.getWarnings()));
      summary.set("notices", findingsArray(report.getNotices()));
      root.set("summary", summary);
      root.put("erechnung", report.getErechnungXml() == null ? "" : report.getErechnungXml());
      root.put(
          "mustang-pruefbericht",
          report.getMustangPruefbericht() == null ? "" : report.getMustangPruefbericht());
      return mapper.writeValueAsString(root);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize report", ex);
    }
  }

  private ObjectNode buildSections(ValidationReport report, boolean pdfInput) {
    Map<String, Severity> worst = new HashMap<>();
    for (String key : CheckSection.ALL) {
      worst.put(key, null);
    }
    for (Finding f : report.getErrors()) {
      bump(worst, f);
    }
    for (Finding f : report.getWarnings()) {
      bump(worst, f);
    }
    for (Finding f : report.getNotices()) {
      bump(worst, f);
    }
    ObjectNode sections = mapper.createObjectNode();
    for (String key : CheckSection.ALL) {
      ObjectNode node = mapper.createObjectNode();
      boolean appl =
          pdfInput
              || !(CheckSection.PDFA.equals(key)
                  || CheckSection.EMBEDDED_XML.equals(key)
                  || CheckSection.METADATA_EMBEDDING.equals(key));
      node.put("applicable", appl);
      Severity s = worst.get(key);
      node.put("status", s == null ? "ok" : s.name().toLowerCase(Locale.ROOT));
      sections.set(key, node);
    }
    return sections;
  }

  private static void bump(Map<String, Severity> worst, Finding f) {
    if (f.section() == null || f.section().isBlank()) {
      return;
    }
    Severity cur = worst.get(f.section());
    if (cur == null || rank(f.severity()) > rank(cur)) {
      worst.put(f.section(), f.severity());
    }
  }

  private static int rank(Severity s) {
    return switch (s) {
      case NOTICE -> 1;
      case WARNING -> 2;
      case ERROR -> 3;
    };
  }

  private ArrayNode findingsArray(List<Finding> findings) {
    ArrayNode arr = mapper.createArrayNode();
    for (Finding f : findings) {
      ObjectNode node = mapper.createObjectNode();
      node.put("engine", f.engine());
      if (f.section() != null) {
        node.put("section", f.section());
      }
      node.put("id", f.id());
      node.put("message", f.message());
      if (f.location() != null && !f.location().isBlank()) {
        node.put("location", f.location());
      } else {
        node.putNull("location");
      }
      arr.add(node);
    }
    return arr;
  }

  private static String jsonVerdict(Verdict verdict) {
    if (verdict == null) {
      return "tool_error";
    }
    return switch (verdict) {
      case CONFORMANT -> "conformant";
      case NONCONFORMANT -> "nonconformant";
      case NOT_ERECHNUNG -> "not_erechnung";
      case TOOL_ERROR -> "tool_error";
    };
  }
}
