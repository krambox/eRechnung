package ai.kkc.erechnung;

import ai.kkc.erechnung.engine.ValidationOrchestrator;
import ai.kkc.erechnung.json.ReportJson;
import ai.kkc.erechnung.model.ValidationReport;
import ai.kkc.erechnung.model.Verdict;
import ai.kkc.erechnung.policy.ConformancePolicy;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fat-JAR entrypoint: {@code java -jar eRechnung.jar validate <PATH>}.
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
      if (args.length != 2) {
        System.err.println("usage: validate <PATH>");
        return 3;
      }
      Path path = Path.of(args[1]);
      ValidationOrchestrator orchestrator = ValidationOrchestrator.createDefault();
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

  private static void printUsage() {
    System.err.println(
        """
        eRechnung — DE B2B e-invoice conformance check (E-Rechnungs-Konformitätsprüfung)

        Usage:
          java -jar eRechnung.jar validate <PATH>

        PATH may be a PDF (Factur-X/ZUGFeRD PDF/A-3 hybrid) or standalone XML (XRechnung/CII/UBL).

        Exit codes:
          0  conformant
          1  nonconformant
          2  not_erechnung
          3  tool/IO error

        JSON is written to stdout only (verdict, summary, erechnung, mustang-pruefbericht).
        """);
  }
}
