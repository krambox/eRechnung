package ai.kkc.erechnung.engine;

import ai.kkc.erechnung.model.DetectedFormat;
import ai.kkc.erechnung.model.EngineFindings;
import ai.kkc.erechnung.model.Finding;
import ai.kkc.erechnung.model.ValidationReport;
import ai.kkc.erechnung.model.Verdict;
import ai.kkc.erechnung.policy.ConformancePolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates Mustang (always) and KoSIT (only for XRechnung). Applies conformance policy.
 */
public final class ValidationOrchestrator {

  private final MustangEngine mustang;
  private final KositEngine kosit;
  private final XmlExtractor xmlExtractor;
  private final ConformancePolicy policy;

  public ValidationOrchestrator(
      MustangEngine mustang,
      KositEngine kosit,
      XmlExtractor xmlExtractor,
      ConformancePolicy policy) {
    this.mustang = mustang;
    this.kosit = kosit;
    this.xmlExtractor = xmlExtractor;
    this.policy = policy;
  }

  public static ValidationOrchestrator createDefault() {
    FormatDetector detector = new FormatDetector();
    return new ValidationOrchestrator(
        new MustangEngine(detector),
        new KositEngine(),
        new XmlExtractor(),
        new ConformancePolicy());
  }

  public ValidationReport validate(Path path) throws IOException {
    if (!Files.isRegularFile(path)) {
      throw new IOException("not a readable file: " + path);
    }
    String filename = path.getFileName().toString();
    String erechnungXml = xmlExtractor.extract(path);
    MustangResult mustangResult = mustang.validate(path);

    if (mustangResult.toolError()) {
      ValidationReport report = new ValidationReport();
      report.setVerdict(Verdict.TOOL_ERROR);
      report.setErechnungXml(erechnungXml);
      for (Finding finding : mustangResult.findings()) {
        report.addFinding(finding);
      }
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("filename", filename);
      metadata.put("format", DetectedFormat.NOT_ERECHNUNG.name().toLowerCase());
      metadata.put("engines", Map.of("mustang", true, "kosit", false));
      report.setMetadata(metadata);
      return report;
    }

    List<Finding> kositFindings = List.of();
    boolean kositRan = false;
    if (shouldRunKosit(mustangResult.format())) {
      // Role merge: KoSIT only for XRechnung profiles — never against plain Factur-X EN16931.
      kositFindings = kosit.validateXml(erechnungXml, filename);
      kositRan = true;
    }

    EngineFindings findings =
        new EngineFindings(mustangResult.format(), mustangResult.findings(), kositFindings);
    Verdict verdict = policy.decide(findings);

    ValidationReport report = new ValidationReport();
    report.setVerdict(verdict);
    report.setErechnungXml(erechnungXml);
    for (Finding finding : findings.all()) {
      report.addFinding(finding);
    }

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("filename", filename);
    metadata.put("format", mustangResult.format().name().toLowerCase());
    metadata.put("profile", mustangResult.profile());
    metadata.put("generation", mustangResult.generation());
    metadata.put("engines", Map.of("mustang", true, "kosit", kositRan));
    report.setMetadata(metadata);
    return report;
  }

  public int exitCode(ValidationReport report) {
    return policy.exitCode(report.getVerdict());
  }

  /** KoSIT runs only for XRechnung (role merge). */
  static boolean shouldRunKosit(DetectedFormat format) {
    return format == DetectedFormat.XRECHNUNG;
  }
}
