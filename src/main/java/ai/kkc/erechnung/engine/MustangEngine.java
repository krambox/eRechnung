package ai.kkc.erechnung.engine;

import ai.kkc.erechnung.model.DetectedFormat;
import ai.kkc.erechnung.model.Finding;
import ai.kkc.erechnung.model.PdfAResult;
import ai.kkc.erechnung.model.Severity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.mustangproject.validator.ZUGFeRDValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Runs Mustang validation and maps the XML report into findings + format. */
public final class MustangEngine {

  /** veraPDF dumps {@code ValidationResult [flavour=3b, …, isCompliant=false]} into {@code <pdf>}. */
  private static final Pattern VERAPDF_FLAVOUR = Pattern.compile("flavour=([\\w.-]+)");
  private static final Pattern VERAPDF_TOTAL =
      Pattern.compile("totalAssertions=(\\d+)");
  private static final Pattern ASSERTION_SPEC =
      Pattern.compile("specification=([^\\],]+)");
  private static final Pattern ASSERTION_CLAUSE = Pattern.compile("clause=([^\\],]+)");
  private static final Pattern ASSERTION_TEST = Pattern.compile("testNumber=(\\d+)");
  private static final Pattern ASSERTION_STATUS = Pattern.compile("status=(\\w+)");
  private static final Pattern ASSERTION_LOCATION_CTX =
      Pattern.compile("location=Location \\[level=([^,]*), context=(.*)\\], locationContext=");
  private static final Pattern ASSERTION_ERROR =
      Pattern.compile("errorMessage=(.*)$", Pattern.DOTALL);

  private final FormatDetector formatDetector;

  public MustangEngine(FormatDetector formatDetector) {
    this.formatDetector = formatDetector;
  }

  public MustangResult validate(Path path) throws IOException {
    byte[] bytes = Files.readAllBytes(path);
    return validate(bytes, path.getFileName().toString());
  }

  public MustangResult validate(byte[] bytes, String filename) {
    if (bytes == null || bytes.length == 0) {
      return MustangResult.notErechnung("empty input");
    }
    ZUGFeRDValidator validator = new ZUGFeRDValidator();
    String reportXml;
    try {
      reportXml = validator.validate(new ByteArrayInputStream(bytes), filename);
    } catch (RuntimeException ex) {
      return MustangResult.toolFailure(
          ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }
    if (validator.hasOptionsError()) {
      return MustangResult.notErechnung("input is neither PDF nor XML e-invoice");
    }
    return parseReport(reportXml, validator.wasCompletelyValid());
  }

  MustangResult parseReport(String reportXml, boolean completelyValid) {
    if (reportXml == null || reportXml.isBlank()) {
      return MustangResult.notErechnung("empty mustang report");
    }
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document doc =
          factory
              .newDocumentBuilder()
              .parse(new InputSource(new StringReader(reportXml)));

      String profile = textOfFirst(doc, "profile");
      String generation = textOfFirst(doc, "version");
      DetectedFormat format = formatDetector.fromProfile(profile);

      List<Finding> findings = new ArrayList<>();
      collectMessages(doc.getElementsByTagName("error"), Severity.ERROR, findings);
      collectMessages(doc.getElementsByTagName("exception"), Severity.ERROR, findings);
      collectMessages(doc.getElementsByTagName("warning"), Severity.WARNING, findings);
      collectMessages(doc.getElementsByTagName("notice"), Severity.NOTICE, findings);

      PdfAResult pdfa = parsePdfA(doc);
      if (PdfAResult.NONCONFORMANT.equals(pdfa.status())
          && findings.stream().noneMatch(f -> "pdfa".equals(f.id()) || "23".equals(f.id()))) {
        String msg =
            pdfa.flavour() == null
                ? "PDF/A validation failed (veraPDF)"
                : "PDF/A validation failed (veraPDF isCompliant=false, flavour="
                    + pdfa.flavour()
                    + ")";
        findings.add(0, Finding.error("mustang", "pdfa", msg));
      }

      // If Mustang summary says invalid but no error nodes were parsed, keep policy honest.
      if (!completelyValid && findings.stream().noneMatch(Finding::isError)) {
        findings.add(
            Finding.error(
                "mustang",
                "summary",
                "Mustang reported invalid without discrete error messages"));
      }

      if (format == DetectedFormat.NOT_ERECHNUNG && profile != null && !profile.isBlank()) {
        findings.add(
            0,
            Finding.notice(
                "mustang",
                "profile",
                "profile not accepted for DE B2B e-invoice mandate: " + profile));
      } else if (format == DetectedFormat.NOT_ERECHNUNG && findings.isEmpty()) {
        findings.add(Finding.notice("mustang", "format", "no acceptable e-invoice profile detected"));
      }

      return new MustangResult(
          format,
          profile,
          generation,
          completelyValid,
          reportXml,
          List.copyOf(findings),
          false,
          pdfa);
    } catch (Exception ex) {
      return MustangResult.toolFailure("failed to parse mustang report: " + ex.getMessage());
    }
  }

  /**
   * Surfaces veraPDF PDF/A result from Mustang's {@code <pdf>} section. Mustang embeds
   * {@code ValidationResult.toString()} (not structured XML) and only emits section 23 when the
   * flavour is not PDF/A-3 — {@code isCompliant=false} otherwise leaves no discrete finding.
   */
  static PdfAResult parsePdfA(Document doc) {
    NodeList pdfNodes = doc.getElementsByTagName("pdf");
    if (pdfNodes.getLength() == 0) {
      return PdfAResult.absent();
    }
    Element pdf = (Element) pdfNodes.item(0);
    String pdfText = pdf.getTextContent() == null ? "" : pdf.getTextContent();

    String flavour = null;
    Matcher flavourMatch = VERAPDF_FLAVOUR.matcher(pdfText);
    if (flavourMatch.find()) {
      flavour = flavourMatch.group(1);
    }
    Integer totalAssertions = null;
    Matcher totalMatch = VERAPDF_TOTAL.matcher(pdfText);
    if (totalMatch.find()) {
      totalAssertions = Integer.valueOf(totalMatch.group(1));
    }
    List<Map<String, Object>> assertions = parseVeraPdfAssertions(pdfText);

    boolean notPdfA3 =
        pdfText.contains("Not a PDF/A-3") || (flavour != null && !isPdfA3Flavour(flavour));
    if (pdfText.contains("isCompliant=false") || notPdfA3) {
      return PdfAResult.of(PdfAResult.NONCONFORMANT, flavour, totalAssertions, assertions);
    }
    if (pdfText.contains("isCompliant=true")) {
      return PdfAResult.of(PdfAResult.CONFORMANT, flavour, totalAssertions, assertions);
    }

    // No veraPDF dump — fall back to <pdf><summary status="…"/>.
    NodeList summaries = pdf.getElementsByTagName("summary");
    if (summaries.getLength() > 0) {
      String status = ((Element) summaries.item(0)).getAttribute("status");
      if ("valid".equalsIgnoreCase(status)) {
        return PdfAResult.of(PdfAResult.CONFORMANT, flavour, totalAssertions, assertions);
      }
    }
    return PdfAResult.of(PdfAResult.NONCONFORMANT, flavour, totalAssertions, assertions);
  }

  /** Parses failed {@code TestAssertion […]} blocks from veraPDF's {@code toString()} dump. */
  static List<Map<String, Object>> parseVeraPdfAssertions(String pdfText) {
    List<Map<String, Object>> out = new ArrayList<>();
    int from = 0;
    while (true) {
      int start = pdfText.indexOf("TestAssertion [", from);
      if (start < 0) {
        break;
      }
      int contentStart = start + "TestAssertion [".length();
      int end = matchingCloseBracket(pdfText, contentStart);
      if (end < 0) {
        break;
      }
      String block = pdfText.substring(contentStart, end);
      from = end + 1;

      String status = matchGroup(ASSERTION_STATUS, block);
      if (status != null && !"failed".equalsIgnoreCase(status)) {
        continue;
      }

      Map<String, Object> item = new LinkedHashMap<>();
      String spec = matchGroup(ASSERTION_SPEC, block);
      String clause = matchGroup(ASSERTION_CLAUSE, block);
      String test = matchGroup(ASSERTION_TEST, block);
      if (spec != null) {
        item.put("specification", spec.trim());
      }
      if (clause != null) {
        item.put("clause", clause.trim());
      }
      if (test != null) {
        item.put("test", Integer.valueOf(test));
      }
      if (status != null) {
        item.put("status", status);
      }
      Matcher loc = ASSERTION_LOCATION_CTX.matcher(block);
      if (loc.find()) {
        item.put("location", loc.group(2).trim());
      }
      String error = matchGroup(ASSERTION_ERROR, block);
      if (error != null && !error.isBlank()) {
        item.put("message", error.trim());
      }
      if (!item.isEmpty()) {
        out.add(item);
      }
    }
    return out;
  }

  /** Index of {@code ]} matching the open bracket before {@code contentStart}. */
  private static int matchingCloseBracket(String s, int contentStart) {
    int depth = 1;
    for (int i = contentStart; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '[') {
        depth++;
      } else if (c == ']') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static String matchGroup(Pattern pattern, String text) {
    Matcher m = pattern.matcher(text);
    return m.find() ? m.group(1) : null;
  }

  private static boolean isPdfA3Flavour(String flavour) {
    String f = flavour.toLowerCase(Locale.ROOT);
    return f.equals("3a")
        || f.equals("3b")
        || f.equals("3u")
        || f.contains("pdfa_3")
        || f.contains("pdf/a-3");
  }

  private static void collectMessages(NodeList nodes, Severity severity, List<Finding> findings) {
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!(node instanceof Element el)) {
        continue;
      }
      String id = el.getAttribute("criterion");
      if (id == null || id.isBlank()) {
        id = el.getAttribute("type");
      }
      String message = el.getTextContent() == null ? "" : el.getTextContent().trim();
      String extractedId = extractBracketId(message);
      if (extractedId != null) {
        id = extractedId;
      }
      if (id == null || id.isBlank()) {
        id = severity.name().toLowerCase(Locale.ROOT);
      }
      findings.add(new Finding(severity, "mustang", id, message));
    }
  }

  private static String extractBracketId(String message) {
    int start = message.indexOf("[ID ");
    if (start < 0) {
      return null;
    }
    int end = message.indexOf(']', start);
    if (end < 0) {
      return null;
    }
    return message.substring(start + 4, end).trim();
  }

  private static String textOfFirst(Document doc, String tag) {
    NodeList list = doc.getElementsByTagName(tag);
    if (list.getLength() == 0) {
      return null;
    }
    String text = list.item(0).getTextContent();
    return text == null ? null : text.trim();
  }
}
