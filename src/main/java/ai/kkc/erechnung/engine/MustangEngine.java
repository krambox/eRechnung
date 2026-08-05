package ai.kkc.erechnung.engine;

import ai.kkc.erechnung.model.CheckSection;
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
import java.util.List;
import java.util.Locale;
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

  private static final Pattern VERAPDF_FLAVOUR = Pattern.compile("flavour=([\\w.-]+)");
  private static final Pattern BRACKET_ID = Pattern.compile("\\[ID\\s+([^\\]]+)\\]");
  private static final Pattern RULE_PREFIX =
      Pattern.compile("\\[(PEPPOL-[A-Z0-9-]+|BR-DE-[A-Z0-9-]+)\\]");

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
      Element pdf = firstElement(doc, "pdf");
      Element xml = firstElement(doc, "xml");
      if (pdf != null) {
        collectMessages(pdf, Severity.ERROR, CheckSection.PDFA, findings);
        collectMessages(pdf, Severity.WARNING, CheckSection.PDFA, findings);
        collectMessages(pdf, Severity.NOTICE, CheckSection.EMBEDDED_XML, findings);
      }
      if (xml != null) {
        collectMessages(xml, Severity.ERROR, CheckSection.SCHEMATRON, findings);
        collectMessages(xml, Severity.WARNING, CheckSection.SCHEMATRON, findings);
        collectMessages(xml, Severity.NOTICE, CheckSection.SCHEMATRON, findings);
      }

      PdfAResult pdfa = parsePdfA(doc);
      boolean hasPdfaError =
          findings.stream()
              .anyMatch(f -> f.isError() && CheckSection.PDFA.equals(f.section()));
      if (PdfAResult.NONCONFORMANT.equals(pdfa.status()) && !hasPdfaError) {
        findings.add(
            0,
            Finding.error(
                "mustang",
                "MUSTANG_23",
                "PDF/A validation failed",
                CheckSection.PDFA,
                null));
      }

      if (!completelyValid && findings.stream().noneMatch(Finding::isError)) {
        findings.add(
            Finding.error(
                "mustang",
                "MUSTANG_summary",
                "Mustang reported invalid without discrete error messages",
                CheckSection.SCHEMATRON,
                null));
      }

      if (format == DetectedFormat.NOT_ERECHNUNG && profile != null && !profile.isBlank()) {
        findings.add(
            0,
            Finding.notice(
                "mustang",
                "profile",
                "profile not accepted for DE B2B e-invoice mandate: " + profile,
                CheckSection.SCHEMATRON,
                null));
      } else if (format == DetectedFormat.NOT_ERECHNUNG && findings.isEmpty()) {
        findings.add(
            Finding.notice(
                "mustang",
                "format",
                "no acceptable e-invoice profile detected",
                CheckSection.SCHEMATRON,
                null));
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

    boolean notPdfA3 =
        pdfText.contains("Not a PDF/A-3") || (flavour != null && !isPdfA3Flavour(flavour));
    if (pdfText.contains("isCompliant=false") || notPdfA3) {
      return PdfAResult.of(PdfAResult.NONCONFORMANT, flavour);
    }
    if (pdfText.contains("isCompliant=true")) {
      return PdfAResult.of(PdfAResult.CONFORMANT, flavour);
    }

    NodeList summaries = pdf.getElementsByTagName("summary");
    if (summaries.getLength() > 0) {
      String status = ((Element) summaries.item(0)).getAttribute("status");
      if ("valid".equalsIgnoreCase(status)) {
        return PdfAResult.of(PdfAResult.CONFORMANT, flavour);
      }
    }
    return PdfAResult.of(PdfAResult.NONCONFORMANT, flavour);
  }

  private static boolean isPdfA3Flavour(String flavour) {
    String f = flavour.toLowerCase(Locale.ROOT);
    return f.equals("3a")
        || f.equals("3b")
        || f.equals("3u")
        || f.contains("pdfa_3")
        || f.contains("pdf/a-3");
  }

  private static void collectMessages(
      Element parent, Severity severity, String defaultSection, List<Finding> findings) {
    String tag =
        switch (severity) {
          case ERROR -> "error";
          case WARNING -> "warning";
          case NOTICE -> "notice";
        };
    // also collect <exception> as errors
    collectTag(parent, tag, severity, defaultSection, findings);
    if (severity == Severity.ERROR) {
      collectTag(parent, "exception", severity, defaultSection, findings);
    }
  }

  private static void collectTag(
      Element parent,
      String tag,
      Severity severity,
      String defaultSection,
      List<Finding> findings) {
    NodeList nodes = parent.getElementsByTagName(tag);
    for (int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      if (!(node instanceof Element el)) {
        continue;
      }
      String type = el.getAttribute("type");
      String location = blankToNull(el.getAttribute("location"));
      String message = el.getTextContent() == null ? "" : el.getTextContent().trim();
      String id = resolveId(type, message);
      String section = sectionFor(type, defaultSection);
      findings.add(new Finding(severity, "mustang", id, message, section, location));
    }
  }

  static String resolveId(String type, String message) {
    String fromId = matchGroup(BRACKET_ID, message);
    if (fromId != null) {
      return fromId.trim();
    }
    String fromRule = matchGroup(RULE_PREFIX, message);
    if (fromRule != null) {
      return fromRule.trim();
    }
    if (type != null && type.matches("\\d+")) {
      return "MUSTANG_" + type;
    }
    if (type != null && !type.isBlank() && !"false".equalsIgnoreCase(type)) {
      return type;
    }
    return "mustang";
  }

  static String sectionFor(String type, String defaultSection) {
    // Parent origin (pdf→pdfa, xml→schematron) is primary; overrides only for unambiguous types.
    if ("18".equals(type)) {
      return CheckSection.SCHEMA;
    }
    if ("17".equals(type)) {
      return CheckSection.EMBEDDED_XML;
    }
    if ("4".equals(type)) {
      return CheckSection.SCHEMATRON;
    }
    return defaultSection;
  }

  private static String matchGroup(Pattern pattern, String text) {
    if (text == null) {
      return null;
    }
    Matcher m = pattern.matcher(text);
    return m.find() ? m.group(1) : null;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }

  private static Element firstElement(Document doc, String tag) {
    NodeList list = doc.getElementsByTagName(tag);
    if (list.getLength() == 0) {
      return null;
    }
    return (Element) list.item(0);
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
