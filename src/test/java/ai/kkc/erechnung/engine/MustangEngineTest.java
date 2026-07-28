package ai.kkc.erechnung.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.kkc.erechnung.model.DetectedFormat;
import ai.kkc.erechnung.model.Severity;
import ai.kkc.erechnung.model.PdfAResult;
import org.junit.jupiter.api.Test;

class MustangEngineTest {

  private final MustangEngine engine = new MustangEngine(new FormatDetector());

  @Test
  void parsesXRechnungProfileAndNoticeFromReport() {
    String report =
        """
        <validation filename="x.xml" datetime="2026-01-01">
          <xml>
            <info>
              <version>2</version>
              <profile>urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0</profile>
            </info>
            <messages>
              <notice type="27">[BR-DE-TMP-32] Lieferdatum fehlt [ID BR-DE-TMP-32]</notice>
            </messages>
            <summary status="valid"/>
          </xml>
          <summary status="valid"/>
        </validation>
        """;
    MustangResult result = engine.parseReport(report, true);
    assertEquals(DetectedFormat.XRECHNUNG, result.format());
    assertEquals(1, result.findings().size());
    assertEquals(Severity.NOTICE, result.findings().get(0).severity());
    assertEquals("BR-DE-TMP-32", result.findings().get(0).id());
  }

  @Test
  void mapsErrorsToFindings() {
    String report =
        """
        <validation>
          <xml>
            <info>
              <profile>urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:en16931</profile>
            </info>
            <messages>
              <error type="12">broken</error>
            </messages>
            <summary status="invalid"/>
          </xml>
          <summary status="invalid"/>
        </validation>
        """;
    MustangResult result = engine.parseReport(report, false);
    assertEquals(DetectedFormat.FACTURX_EN16931, result.format());
    assertTrue(result.findings().stream().anyMatch(f -> f.severity() == Severity.ERROR));
    assertEquals(PdfAResult.ABSENT, result.pdfa().status());
  }

  @Test
  void extractsPdfANonconformantFromVeraPdfDump() {
    String report =
        """
        <validation>
          <pdf>
            ValidationResult [flavour=3b, totalAssertions=10, assertions=[TestAssertion [ruleId=RuleId [specification=ISO 19005-3:2012, clause=6.8, testNumber=3], status=failed, message=long rule text, location=Location [level=CosDocument, context=root/EmbeddedFiles[0]], locationContext=null, errorMessage=AFRelationship missing], TestAssertion [ruleId=RuleId [specification=ISO 19005-3:2012, clause=6.8, testNumber=1], status=failed, message=mime, location=Location [level=CosDocument, context=root/EF[0]], locationContext=null, errorMessage=MIME type null]], isCompliant=false]
            <info><signature>unknown</signature></info>
            <messages>
              <error type="11">XMP Metadata: ConformanceLevel not found</error>
            </messages>
            <summary status="invalid"/>
          </pdf>
          <xml>
            <info>
              <version>2</version>
              <profile>urn:cen.eu:en16931:2017</profile>
            </info>
            <summary status="valid"/>
          </xml>
          <summary status="invalid"/>
        </validation>
        """;
    MustangResult result = engine.parseReport(report, false);
    assertEquals(PdfAResult.NONCONFORMANT, result.pdfa().status());
    assertEquals("3b", result.pdfa().flavour());
    assertEquals(10, result.pdfa().totalAssertions());
    assertEquals(2, result.pdfa().assertions().size());
    assertEquals("6.8", result.pdfa().assertions().get(0).get("clause"));
    assertEquals(3, result.pdfa().assertions().get(0).get("test"));
    assertEquals("AFRelationship missing", result.pdfa().assertions().get(0).get("message"));
    assertEquals("root/EmbeddedFiles[0]", result.pdfa().assertions().get(0).get("location"));
    assertTrue(result.findings().stream().anyMatch(f -> "pdfa".equals(f.id())));
  }

  @Test
  void extractsPdfAConformantFromVeraPdfDump() {
    String report =
        """
        <validation>
          <pdf>
            ValidationResult [flavour=3b, totalAssertions=10, assertions=[], isCompliant=true]
            <info><signature>Mustang</signature></info>
            <summary status="valid"/>
          </pdf>
          <xml>
            <info>
              <profile>urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:en16931</profile>
            </info>
            <summary status="valid"/>
          </xml>
          <summary status="valid"/>
        </validation>
        """;
    MustangResult result = engine.parseReport(report, true);
    assertEquals(PdfAResult.CONFORMANT, result.pdfa().status());
    assertEquals("3b", result.pdfa().flavour());
    assertTrue(result.findings().stream().noneMatch(f -> "pdfa".equals(f.id())));
  }

  @Test
  void xmlOnlyReportMarksPdfAAbsent() {
    String report =
        """
        <validation filename="x.xml">
          <xml>
            <info>
              <profile>urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0</profile>
            </info>
            <summary status="valid"/>
          </xml>
          <summary status="valid"/>
        </validation>
        """;
    MustangResult result = engine.parseReport(report, true);
    assertEquals(PdfAResult.ABSENT, result.pdfa().status());
  }
}
