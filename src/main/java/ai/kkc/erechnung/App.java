package ai.kkc.erechnung;

import ai.kkc.erechnung.engine.ValidationOrchestrator;
import ai.kkc.erechnung.json.ReportJson;
import ai.kkc.erechnung.model.ValidationReport;
import ai.kkc.erechnung.model.Verdict;
import ai.kkc.erechnung.policy.ConformancePolicy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fat-JAR entrypoint: {@code java -jar eRechnung.jar validate …}.
 *
 * <p>One-shot: {@code validate [--max-xml-size=5MiB] <PATH>} — JSON on stdout.
 *
 * <p>Daemon: {@code validate --serve [--port=8092] [--bind=127.0.0.1] [--queue-wait=20s]
 * [--max-xml-size=5MiB]} — localhost HTTP, warm JVM.
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
      if (parsed.serve()) {
        return new ValidateServe(
                parsed.bind(), parsed.port(), parsed.queueWait(), parsed.maxXmlBytes())
            .runBlocking();
      }
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

  record ParsedValidate(
      boolean serve,
      String path,
      long maxXmlBytes,
      String bind,
      int port,
      Duration queueWait) {}

  /**
   * {@code validate [--serve] [--bind=127.0.0.1] [--port=8092] [--queue-wait=20s]
   * [--max-xml-size=5MiB] [<PATH>]}.
   */
  static ParsedValidate parseValidateArgs(String[] args) {
    long maxXmlBytes = XmlSizeLimit.DEFAULT_BYTES;
    boolean serve = false;
    String bind = "127.0.0.1";
    int port = 8092;
    Duration queueWait = Duration.ofSeconds(20);
    List<String> positional = new ArrayList<>();
    for (int i = 1; i < args.length; i++) {
      String a = args[i];
      if ("--serve".equals(a)) {
        serve = true;
      } else if (a.startsWith("--max-xml-size=")) {
        maxXmlBytes = XmlSizeLimit.parseBytes(a.substring("--max-xml-size=".length()));
      } else if ("--max-xml-size".equals(a)) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("--max-xml-size requires a value (e.g. 5MiB)");
        }
        maxXmlBytes = XmlSizeLimit.parseBytes(args[++i]);
      } else if (a.startsWith("--bind=")) {
        bind = a.substring("--bind=".length()).trim();
        if (bind.isEmpty()) {
          throw new IllegalArgumentException("--bind requires a host");
        }
      } else if ("--bind".equals(a)) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("--bind requires a host");
        }
        bind = args[++i];
      } else if (a.startsWith("--port=")) {
        port = Integer.parseInt(a.substring("--port=".length()).trim());
      } else if ("--port".equals(a)) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("--port requires a value");
        }
        port = Integer.parseInt(args[++i]);
      } else if (a.startsWith("--queue-wait=")) {
        queueWait = parseDurationSeconds(a.substring("--queue-wait=".length()));
      } else if ("--queue-wait".equals(a)) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("--queue-wait requires a value (e.g. 20s)");
        }
        queueWait = parseDurationSeconds(args[++i]);
      } else if (a.startsWith("-")) {
        throw new IllegalArgumentException("unknown option: " + a);
      } else {
        positional.add(a);
      }
    }
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("--port out of range");
    }
    if (queueWait.isNegative() || queueWait.isZero()) {
      throw new IllegalArgumentException("--queue-wait must be > 0");
    }
    if (serve) {
      if (!positional.isEmpty()) {
        throw new IllegalArgumentException("usage: validate --serve [options] (no PATH)");
      }
      return new ParsedValidate(true, null, maxXmlBytes, bind, port, queueWait);
    }
    if (positional.size() != 1) {
      throw new IllegalArgumentException("usage: validate [--max-xml-size=5MiB] <PATH>");
    }
    return new ParsedValidate(false, positional.get(0), maxXmlBytes, bind, port, queueWait);
  }

  /** {@code 20s}, {@code 20}, or plain seconds. */
  static Duration parseDurationSeconds(String spec) {
    String s = spec.trim().toLowerCase();
    if (s.endsWith("s")) {
      s = s.substring(0, s.length() - 1).trim();
    }
    long seconds = Long.parseLong(s);
    if (seconds <= 0) {
      throw new IllegalArgumentException("invalid --queue-wait '" + spec + "'");
    }
    return Duration.ofSeconds(seconds);
  }

  private static void printUsage() {
    System.err.println(
        """
        eRechnung — DE B2B e-invoice conformance check (E-Rechnungs-Konformitätsprüfung)

        Usage:
          java -jar eRechnung.jar validate [--max-xml-size=5MiB] <PATH>
          java -jar eRechnung.jar validate --serve [--bind=127.0.0.1] [--port=8092] [--queue-wait=20s] [--max-xml-size=5MiB]

        PATH may be a PDF (Factur-X/ZUGFeRD PDF/A-3 hybrid) or standalone XML (XRechnung/CII/UBL).

        --serve starts a localhost HTTP daemon (warm JVM):
          GET  /healthz
          POST /validate   multipart field "file" (.pdf / .xml)
          One validation worker; queue wait default 20s then HTTP 503.

        Options:
          --max-xml-size=SIZE   Max invoice XML size before validation (default 5MiB).
          --bind=HOST           Serve bind address (default 127.0.0.1).
          --port=N              Serve port (default 8092).
          --queue-wait=DURATION Max time in queue before 503 (default 20s).

        Exit codes (one-shot validate):
          0  conformant
          1  nonconformant
          2  not_erechnung
          3  tool/IO error (including XML over --max-xml-size)

        JSON is written to stdout only for one-shot mode (verdict, summary, erechnung,
        mustang-pruefbericht). Serve mode returns the same JSON over HTTP.
        """);
  }
}
