package ai.kkc.erechnung.engine;

import ai.kkc.erechnung.XmlSizeLimit;
import ai.kkc.erechnung.model.DetectedFormat;
import ai.kkc.erechnung.model.EngineFindings;
import ai.kkc.erechnung.model.Finding;
import ai.kkc.erechnung.model.ValidationReport;
import ai.kkc.erechnung.model.Verdict;
import ai.kkc.erechnung.policy.ConformancePolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orchestrates Mustang (always) and KoSIT (only for XRechnung). Applies conformance policy.
 */
public final class ValidationOrchestrator {

  /** Keep in sync with {@code mustang.version} in pom.xml. */
  static final String MUSTANG_VERSION = "2.24.0";

  private final MustangEngine mustang;
  private final KositEngine kosit;
  private final XmlExtractor xmlExtractor;
  private final ConformancePolicy policy;
  private final long maxXmlBytes;

  public ValidationOrchestrator(
      MustangEngine mustang,
      KositEngine kosit,
      XmlExtractor xmlExtractor,
      ConformancePolicy policy) {
    this(mustang, kosit, xmlExtractor, policy, XmlSizeLimit.DEFAULT_BYTES);
  }

  public ValidationOrchestrator(
      MustangEngine mustang,
      KositEngine kosit,
      XmlExtractor xmlExtractor,
      ConformancePolicy policy,
      long maxXmlBytes) {
    this.mustang = mustang;
    this.kosit = kosit;
    this.xmlExtractor = xmlExtractor;
    this.policy = policy;
    this.maxXmlBytes = maxXmlBytes;
  }

  public static ValidationOrchestrator createDefault() {
    return createDefault(XmlSizeLimit.DEFAULT_BYTES);
  }

  public static ValidationOrchestrator createDefault(long maxXmlBytes) {
    FormatDetector detector = new FormatDetector();
    return new ValidationOrchestrator(
        new MustangEngine(detector),
        new KositEngine(),
        new XmlExtractor(),
        new ConformancePolicy(),
        maxXmlBytes);
  }

  public ValidationReport validate(Path path) throws IOException {
    if (!Files.isRegularFile(path)) {
      throw new IOException("not a readable file: " + path);
    }
    long started = System.currentTimeMillis();
    String filename = path.getFileName().toString();
    boolean pdfInput = filename.toLowerCase(Locale.ROOT).endsWith(".pdf");

    // Standalone XML: refuse before loading/validating when the file itself is too large.
    if (!pdfInput && Files.size(path) > maxXmlBytes) {
      return sizeLimitReport(filename, pdfInput, Files.size(path), started);
    }

    String erechnungXml = xmlExtractor.extract(path);
    long xmlBytes =
        erechnungXml.isEmpty()
            ? 0L
            : erechnungXml.getBytes(StandardCharsets.UTF_8).length;
    // PDF hybrid (or XML that passed the file-size gate): limit on extracted invoice XML.
    if (xmlBytes > maxXmlBytes) {
      return sizeLimitReport(filename, pdfInput, xmlBytes, started);
    }

    MustangResult mustangResult = mustang.validate(path);

    ValidationReport report = new ValidationReport();
    report.setErechnungXml(erechnungXml);
    report.setMustangPruefbericht(
        mustangResult.reportXml() == null ? "" : mustangResult.reportXml());

    if (mustangResult.toolError()) {
      report.setVerdict(Verdict.TOOL_ERROR);
      for (Finding finding : mustangResult.findings()) {
        report.addFinding(finding);
      }
      report.setSummary(
          baseSummary(
              filename,
              pdfInput,
              DetectedFormat.NOT_ERECHNUNG.name().toLowerCase(Locale.ROOT),
              null,
              null,
              false,
              started));
      return report;
    }

    List<Finding> kositFindings = List.of();
    boolean kositRan = false;
    if (shouldRunKosit(mustangResult.format())) {
      kositFindings = kosit.validateXml(erechnungXml, filename);
      kositRan = true;
    }

    EngineFindings findings =
        new EngineFindings(mustangResult.format(), mustangResult.findings(), kositFindings);
    report.setVerdict(policy.decide(findings));
    for (Finding finding : findings.all()) {
      report.addFinding(finding);
    }
    report.setSummary(
        baseSummary(
            filename,
            pdfInput,
            mustangResult.format().name().toLowerCase(Locale.ROOT),
            mustangResult.profile(),
            mustangResult.generation(),
            kositRan,
            started));
    return report;
  }

  private ValidationReport sizeLimitReport(
      String filename, boolean pdfInput, long xmlBytes, long startedMs) {
    ValidationReport report = new ValidationReport();
    report.setVerdict(Verdict.TOOL_ERROR);
    report.setErechnungXml("");
    report.setMustangPruefbericht("");
    Map<String, Object> summary =
        baseSummary(
            filename,
            pdfInput,
            DetectedFormat.NOT_ERECHNUNG.name().toLowerCase(Locale.ROOT),
            null,
            null,
            false,
            startedMs);
    summary.put("engines", Map.of("mustang", false, "kosit", false));
    summary.put("xml_bytes", xmlBytes);
    summary.put("max_xml_bytes", maxXmlBytes);
    summary.put(
        "error",
        "invoice XML exceeds size limit: "
            + xmlBytes
            + " bytes > "
            + maxXmlBytes
            + " bytes ("
            + XmlSizeLimit.formatMiB(maxXmlBytes)
            + "); raise --max-xml-size or shrink the invoice");
    report.setSummary(summary);
    return report;
  }

  private static Map<String, Object> baseSummary(
      String filename,
      boolean pdfInput,
      String format,
      String profile,
      String generation,
      boolean kositRan,
      long startedMs) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("filename", filename);
    summary.put("duration_ms", System.currentTimeMillis() - startedMs);
    summary.put("format", format);
    if (generation != null) {
      summary.put("generation", generation);
    }
    if (profile != null) {
      summary.put("profile", profile);
    }
    summary.put("pdf_input", pdfInput);
    if (pdfInput) {
      summary.put("attachment", "factur-x.xml");
    }
    summary.put("validator", Map.of("mustang", MUSTANG_VERSION));
    summary.put("engines", Map.of("mustang", true, "kosit", kositRan));
    return summary;
  }

  public int exitCode(ValidationReport report) {
    return policy.exitCode(report.getVerdict());
  }

  /** KoSIT runs only for XRechnung (role merge). */
  static boolean shouldRunKosit(DetectedFormat format) {
    return format == DetectedFormat.XRECHNUNG;
  }
}
