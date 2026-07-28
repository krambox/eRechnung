package ai.kkc.erechnung.json;

import ai.kkc.erechnung.model.Finding;
import ai.kkc.erechnung.model.ValidationReport;
import ai.kkc.erechnung.model.Verdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Locale;

/** Serializes {@link ValidationReport} to the CLI JSON contract (stdout only). */
public final class ReportJson {

  private final ObjectMapper mapper;

  public ReportJson() {
    this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  }

  public String toJson(ValidationReport report) {
    try {
      ObjectNode root = mapper.createObjectNode();
      root.put("verdict", jsonVerdict(report.getVerdict()));
      root.set("errors", findingsArray(report.getErrors()));
      root.set("warnings", findingsArray(report.getWarnings()));
      root.set("notices", findingsArray(report.getNotices()));
      root.set("metadata", mapper.valueToTree(report.getMetadata()));
      root.put("erechnung_xml", report.getErechnungXml());
      return mapper.writeValueAsString(root);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to serialize report", ex);
    }
  }

  private ArrayNode findingsArray(List<Finding> findings) {
    ArrayNode arr = mapper.createArrayNode();
    for (Finding f : findings) {
      ObjectNode node = mapper.createObjectNode();
      node.put("engine", f.engine());
      node.put("id", f.id());
      node.put("message", f.message());
      node.put("severity", f.severity().name().toLowerCase(Locale.ROOT));
      arr.add(node);
    }
    return arr;
  }

  private static String jsonVerdict(Verdict verdict) {
    return switch (verdict) {
      case CONFORMANT -> "conformant";
      case NONCONFORMANT -> "nonconformant";
      case NOT_ERECHNUNG -> "not_erechnung";
      case TOOL_ERROR -> "tool_error";
    };
  }
}
