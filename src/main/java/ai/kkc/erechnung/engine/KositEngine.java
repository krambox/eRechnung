package ai.kkc.erechnung.engine;

import ai.kkc.erechnung.model.CheckSection;
import ai.kkc.erechnung.model.Finding;
import ai.kkc.erechnung.model.Severity;
import de.kosit.validationtool.api.Check;
import de.kosit.validationtool.api.Configuration;
import de.kosit.validationtool.api.Input;
import de.kosit.validationtool.api.InputFactory;
import de.kosit.validationtool.api.Result;
import de.kosit.validationtool.api.XmlError;
import de.kosit.validationtool.impl.DefaultCheck;
import de.kosit.validationtool.impl.xml.ProcessorProvider;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.oclc.purl.dsdl.svrl.FailedAssert;
import org.oclc.purl.dsdl.svrl.Text;

/**
 * KoSIT XRechnung validator. Only invoked when Mustang detected an XRechnung profile (role merge).
 */
public final class KositEngine {

  private final Check check;

  public KositEngine() {
    this(loadDefault());
  }

  KositEngine(Check check) {
    this.check = Objects.requireNonNull(check);
  }

  private static Check loadDefault() {
    try {
      URL scenarios =
          KositEngine.class.getClassLoader().getResource("kosit-xrechnung/scenarios.xml");
      if (scenarios == null) {
        throw new IllegalStateException("bundled kosit-xrechnung/scenarios.xml missing");
      }
      URI scenariosUri = scenarios.toURI();
      Configuration config =
          Configuration.load(scenariosUri).build(ProcessorProvider.getProcessor());
      return new DefaultCheck(config);
    } catch (Exception ex) {
      throw new IllegalStateException("failed to initialize KoSIT XRechnung configuration", ex);
    }
  }

  public List<Finding> validateXml(String xml, String name) {
    if (xml == null || xml.isBlank()) {
      return List.of(
          Finding.error(
              "kosit", "empty", "no XML for KoSIT validation", CheckSection.SCHEMA, null));
    }
    byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
    Input input =
        InputFactory.read(new ByteArrayInputStream(bytes), name == null ? "invoice.xml" : name);
    Result result = check.checkInput(input);
    return mapResult(result);
  }

  List<Finding> mapResult(Result result) {
    List<Finding> findings = new ArrayList<>();
    if (!result.isProcessingSuccessful()) {
      for (String err : result.getProcessingErrors()) {
        findings.add(Finding.error("kosit", "processing", err, CheckSection.SCHEMA, null));
      }
      if (findings.isEmpty()) {
        findings.add(
            Finding.error(
                "kosit",
                "processing",
                "KoSIT processing unsuccessful",
                CheckSection.SCHEMA,
                null));
      }
      return findings;
    }
    if (!result.isWellformed()) {
      findings.add(
          Finding.error(
              "kosit", "wellformed", "XML is not well-formed", CheckSection.SCHEMA, null));
    }
    for (XmlError violation :
        result.getSchemaViolations() == null ? List.<XmlError>of() : result.getSchemaViolations()) {
      String msg = violation.getMessage() == null ? "schema violation" : violation.getMessage();
      findings.add(Finding.error("kosit", "schema", msg, CheckSection.SCHEMA, null));
    }
    for (FailedAssert failed :
        result.getFailedAsserts() == null
            ? List.<FailedAssert>of()
            : result.getFailedAsserts()) {
      findings.add(mapFailedAssert(failed));
    }
    return List.copyOf(findings);
  }

  private static Finding mapFailedAssert(FailedAssert failed) {
    String id = failed.getId() == null ? "schematron" : failed.getId();
    String message = textContent(failed.getText());
    if (message.isBlank()) {
      message = failed.getTest() == null ? id : failed.getTest();
    }
    String location = failed.getLocation();
    Severity severity = severityFromFlag(failed.getFlag());
    return new Finding(severity, "kosit", id, message, CheckSection.SCHEMATRON, location);
  }

  private static Severity severityFromFlag(String flag) {
    if (flag == null || flag.isBlank()) {
      return Severity.ERROR;
    }
    String f = flag.trim().toLowerCase(Locale.ROOT);
    return switch (f) {
      case "warning", "warn" -> Severity.WARNING;
      case "information", "info", "notice" -> Severity.NOTICE;
      default -> Severity.ERROR;
    };
  }

  private static String textContent(Text text) {
    if (text == null || text.getContent() == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Object part : text.getContent()) {
      if (part != null) {
        sb.append(part);
      }
    }
    return sb.toString().trim();
  }
}
