package ai.kkc.erechnung.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** PDF/A evaluation extracted from Mustang/veraPDF (absent for standalone XML). */
public record PdfAResult(
    String status, String flavour, Integer totalAssertions, List<Map<String, Object>> assertions) {

  public static final String ABSENT = "absent";
  public static final String CONFORMANT = "conformant";
  public static final String NONCONFORMANT = "nonconformant";

  public static PdfAResult absent() {
    return new PdfAResult(ABSENT, null, null, List.of());
  }

  public static PdfAResult of(String status, String flavour) {
    return new PdfAResult(status, flavour, null, List.of());
  }

  public static PdfAResult of(
      String status,
      String flavour,
      Integer totalAssertions,
      List<Map<String, Object>> assertions) {
    return new PdfAResult(
        status, flavour, totalAssertions, assertions == null ? List.of() : List.copyOf(assertions));
  }

  public Map<String, Object> toMetadata() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("status", status);
    map.put("engine", "verapdf");
    if (flavour != null && !flavour.isBlank()) {
      map.put("flavour", flavour);
    }
    if (totalAssertions != null) {
      map.put("total_assertions", totalAssertions);
    }
    if (assertions != null && !assertions.isEmpty()) {
      map.put("assertions", assertions);
    }
    return map;
  }
}
