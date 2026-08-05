package ai.kkc.erechnung.corpus;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.kkc.erechnung.engine.ValidationOrchestrator;
import ai.kkc.erechnung.model.ValidationReport;
import ai.kkc.erechnung.model.Verdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Validates every PDF/XML under the {@code corpus} submodule against path-derived expectations
 * from <a href="https://github.com/ZUGFeRD/corpus">ZUGFeRD/corpus</a>.
 *
 * <p>Run with {@code mvn -Pcorpus test}. Excluded from the default Surefire run (slow).
 */
@Tag("corpus")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorpusValidationTest {

  private static final Path CORPUS = Path.of("corpus");

  private ValidationOrchestrator orchestrator;

  @BeforeAll
  void setUp() {
    assumeTrue(
        Files.isDirectory(CORPUS.resolve("ZUGFeRDv2")),
        "corpus submodule missing — run: git submodule update --init");
    orchestrator = ValidationOrchestrator.createDefault();
  }

  static Stream<Path> invoiceFiles() throws IOException {
    assumeTrue(Files.isDirectory(CORPUS), "corpus submodule not present");
    List<Path> files = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(CORPUS)) {
      walk.filter(Files::isRegularFile)
          .filter(p -> !p.toString().contains("/.git/"))
          .filter(CorpusValidationTest::isInvoiceFile)
          .forEach(files::add);
    }
    files.sort(Path::compareTo);
    return files.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invoiceFiles")
  void matchesCorpusExpectation(Path file) throws Exception {
    Optional<CorpusExpectation.Expected> expectedOpt =
        CorpusExpectation.forPath(CORPUS.toAbsolutePath().normalize(), file);
    assumeTrue(expectedOpt.isPresent());
    CorpusExpectation.Expected expected = expectedOpt.get();
    assumeTrue(
        expected.kind() == CorpusExpectation.Kind.MATCH_VERDICT,
        () -> "skip: " + expected.reason());

    ValidationReport report = orchestrator.validate(file);
    Verdict actual = report.getVerdict();
    assertTrue(
        CorpusExpectation.matches(expected, actual),
        () ->
            file
                + " → expected "
                + expected.verdict()
                + " ("
                + expected.reason()
                + ") but was "
                + actual
                + " summary="
                + report.getSummary());
  }

  private static boolean isInvoiceFile(Path p) {
    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".pdf") || name.endsWith(".xml");
  }
}
