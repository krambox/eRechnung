package ai.kkc.erechnung.corpus;

import ai.kkc.erechnung.model.Verdict;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps ZUGFeRD/corpus path layout to the verdict this project expects.
 *
 * <p>Aligned with {@code FormatDetector}: EN16931/Extended/XRechnung/BASIC(EN16931) → can be
 * conformant; MINIMUM/BASIC-WL → {@code not_erechnung}. Folder {@code fail} → reject. Drifts vs
 * Mustang 2.24.0 are listed in {@code corpus-expectation-overrides.tsv}.
 */
public final class CorpusExpectation {

  public enum Kind {
    SKIP,
    MATCH_VERDICT
  }

  public record Expected(Kind kind, Verdict verdict, boolean rejectOnly, String reason) {
    static Expected skip(String reason) {
      return new Expected(Kind.SKIP, null, false, reason);
    }

    static Expected exact(Verdict v, String reason) {
      return new Expected(Kind.MATCH_VERDICT, v, false, reason);
    }

    static Expected reject(String reason) {
      return new Expected(Kind.MATCH_VERDICT, Verdict.NONCONFORMANT, true, reason);
    }
  }

  private static final Map<String, Expected> OVERRIDES = loadOverrides();

  private CorpusExpectation() {}

  public static Optional<Expected> forPath(Path corpusRoot, Path file) {
    Path absFile = file.toAbsolutePath().normalize();
    Path absRoot = corpusRoot.toAbsolutePath().normalize();
    Path rel = absRoot.relativize(absFile);
    String relKey = rel.toString().replace('\\', '/');
    Expected override = OVERRIDES.get(relKey);
    if (override != null) {
      return Optional.of(override);
    }

    String path = relKey.toLowerCase(Locale.ROOT);
    String[] parts = path.split("/");
    String filename = parts[parts.length - 1];

    if (contains(parts, "zugferdv1")) {
      return Optional.of(Expected.skip("ZUGFeRD v1 out of DE B2B EN16931 test scope"));
    }

    if (contains(parts, "fail")) {
      return Optional.of(Expected.reject("corpus fail"));
    }

    boolean labeledPass = contains(parts, "correct") || contains(parts, "valid");
    boolean xmlRechnung = parts.length > 0 && "xml-rechnung".equals(parts[0]);
    if (!labeledPass && !xmlRechnung) {
      return Optional.of(Expected.skip("no correct/fail/valid/XML-Rechnung label"));
    }

    if (filename.contains("not_validating")) {
      return Optional.of(Expected.exact(Verdict.NONCONFORMANT, "filename marks invalid"));
    }

    if (contains(parts, "fatturapa")) {
      return Optional.of(Expected.exact(Verdict.NOT_ERECHNUNG, "fatturaPA"));
    }

    if (isMinimumOrBasicWl(parts, filename)) {
      return Optional.of(Expected.exact(Verdict.NOT_ERECHNUNG, "MINIMUM/BASIC-WL"));
    }

    // PEPPOL Valid + EN16931-family / BASIC EN16931 / Extended / XRechnung / XML-Rechnung
    return Optional.of(Expected.exact(Verdict.CONFORMANT, "EN16931-family"));
  }

  public static boolean matches(Expected expected, Verdict actual) {
    if (expected.kind() == Kind.SKIP) {
      return true;
    }
    if (expected.rejectOnly()) {
      return actual == Verdict.NONCONFORMANT || actual == Verdict.NOT_ERECHNUNG;
    }
    return actual == expected.verdict();
  }

  private static boolean isMinimumOrBasicWl(String[] parts, String filename) {
    String hay = String.join("/", parts) + "/" + filename;
    return hay.contains("minimum")
        || hay.contains("basicwl")
        || hay.contains("basic-wl")
        || hay.contains("basic_wl")
        || hay.contains("basic wl");
  }

  private static boolean contains(String[] parts, String needle) {
    for (String p : parts) {
      if (p.equals(needle)) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, Expected> loadOverrides() {
    Map<String, Expected> map = new HashMap<>();
    var in =
        CorpusExpectation.class
            .getClassLoader()
            .getResourceAsStream("corpus-expectation-overrides.tsv");
    if (in == null) {
      return map;
    }
    try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        String[] cols = line.split("\\t");
        if (cols.length < 2) {
          continue;
        }
        String path = cols[0].trim();
        String verdict = cols[1].trim().toLowerCase(Locale.ROOT);
        map.put(
            path,
            switch (verdict) {
              case "skip" -> Expected.skip("override");
              case "conformant" -> Expected.exact(Verdict.CONFORMANT, "override");
              case "nonconformant" -> Expected.exact(Verdict.NONCONFORMANT, "override");
              case "not_erechnung" -> Expected.exact(Verdict.NOT_ERECHNUNG, "override");
              default -> Expected.skip("bad override: " + verdict);
            });
      }
    } catch (IOException ex) {
      throw new IllegalStateException("failed to read corpus-expectation-overrides.tsv", ex);
    }
    return map;
  }
}
