package ai.kkc.erechnung.engine;

import ai.kkc.erechnung.model.DetectedFormat;
import ai.kkc.erechnung.model.Finding;
import java.util.List;

/** Parsed Mustang validation outcome (engine-only, no policy). */
public record MustangResult(
    DetectedFormat format,
    String profile,
    String generation,
    boolean completelyValid,
    String reportXml,
    List<Finding> findings,
    boolean toolError) {

  public static MustangResult notErechnung(String reason) {
    return new MustangResult(
        DetectedFormat.NOT_ERECHNUNG,
        null,
        null,
        false,
        "",
        List.of(Finding.notice("mustang", "format", reason)),
        false);
  }

  public static MustangResult toolFailure(String message) {
    return new MustangResult(
        DetectedFormat.NOT_ERECHNUNG,
        null,
        null,
        false,
        "",
        List.of(Finding.error("mustang", "exception", message)),
        true);
  }
}
