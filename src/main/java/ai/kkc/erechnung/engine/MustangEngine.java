package ai.kkc.erechnung.engine;

import ai.kkc.erechnung.model.DetectedFormat;
import ai.kkc.erechnung.model.Finding;
import ai.kkc.erechnung.model.Severity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import org.mustangproject.validator.ZUGFeRDValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Runs Mustang validation and maps the XML report into findings + format. */
public final class MustangEngine {

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
          format, profile, generation, completelyValid, reportXml, List.copyOf(findings), false);
    } catch (Exception ex) {
      return MustangResult.toolFailure("failed to parse mustang report: " + ex.getMessage());
    }
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
