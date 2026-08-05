package ai.kkc.erechnung;

import ai.kkc.erechnung.engine.ValidationOrchestrator;
import ai.kkc.erechnung.json.ReportJson;
import ai.kkc.erechnung.model.ValidationReport;
import ai.kkc.erechnung.model.Verdict;
import ai.kkc.erechnung.policy.ConformancePolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fat-JAR entrypoint: {@code java -jar eRechnung.jar validate [--max-xml-size=5MiB] <PATH>}.
 *
 * <p>JSON on stdout only. Exit codes: 0 conformant, 1 nonconformant, 2 not_erechnung, 3 tool/IO.
 */
public final class App {

  private App() {}

  public static void main(String[] args) {
    System.exit(run(args));
  }

  static int run(String[] args) {
    ReportJson json = new ReportJson();
    ConformancePolicy policy = new ConformancePolicy();
    try {
      if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
        printUsage();
        return args.length == 0 ? 3 : 0;
      }
      if (!"validate".equals(args[0])) {
        System.err.println("unknown command: " + args[0]);
        printUsage();
        return 3;
      }
      ParsedValidate parsed = parseValidateArgs(args);
      Path path = Path.of(parsed.path());
      ValidationOrchestrator orchestrator = ValidationOrchestrator.createDefault(parsed.maxXmlBytes());
      ValidationReport report = orchestrator.validate(path);
      System.out.println(json.toJson(report));
      return orchestrator.exitCode(report);
    } catch (Exception ex) {
      ValidationReport errorReport = new ValidationReport();
      errorReport.setVerdict(Verdict.TOOL_ERROR);
      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put(
          "error",
          ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      errorReport.setSummary(summary);
      errorReport.setErechnungXml("");
      errorReport.setMustangPruefbericht("");
      try {
        System.out.println(json.toJson(errorReport));
      } catch (Exception ignored) {
        System.err.println("tool error: " + ex.getMessage());
      }
      return policy.exitCode(Verdict.TOOL_ERROR);
    }
  }

  record ParsedValidate(String path, long maxXmlBytes) {}

  /** {@code validate [--max-xml-size=5MiB|--max-xml-size 5MiB] <PATH>} */
  static ParsedValidate parseValidateArgs(String[] args) {
    long maxXmlBytes = XmlSizeLimit.DEFAULT_BYTES;
    List<String> positional = new ArrayList<>();
    for (int i = 1; i < args.length; i++) {
      String a = args[i];
      if (a.startsWith("--max-xml-size=")) {
        maxXmlBytes = XmlSizeLimit.parseBytes(a.substring("--max-xml-size=".length()));
      } else if ("--max-xml-size".equals(a)) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("--max-xml-size requires a value (e.g. 5MiB)");
        }
        maxXmlBytes = XmlSizeLimit.parseBytes(args[++i]);
      } else if (a.startsWith("-")) {
        throw new IllegalArgumentException("unknown option: " + a);
      } else {
        positional.add(a);
      }
    }
    if (positional.size() != 1) {
      throw new IllegalArgumentException("usage: validate [--max-xml-size=5MiB] <PATH>");
    }
    return new ParsedValidate(positional.get(0), maxXmlBytes);
  }

  private static void printUsage() {
    System.err.println(
        """
        eRechnung — DE B2B e-invoice conformance check (E-Rechnungs-Konformitätsprüfung)

        Usage:
          java -jar eRechnung.jar validate [--max-xml-size=5MiB] <PATH>

        PATH may be a PDF (Factur-X/ZUGFeRD PDF/A-3 hybrid) or standalone XML (XRechnung/CII/UBL).

        Options:
          --max-xml-size=SIZE   Max invoice XML size before validation (default 5MiB).
                                Applies to standalone XML files and XML embedded in PDF.
                                Examples: 5MiB, 512KiB, 1048576

        Exit codes:
          0  conformant
          1  nonconformant
          2  not_erechnung
          3  tool/IO error (including XML over --max-xml-size)

        JSON is written to stdout only (verdict, summary, erechnung, mustang-pruefbericht).
        """);
  }
}
