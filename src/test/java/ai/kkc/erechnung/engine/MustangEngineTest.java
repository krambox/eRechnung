package ai.kkc.erechnung.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.kkc.erechnung.model.CheckSection;
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
    assertEquals(CheckSection.SCHEMATRON, result.findings().get(0).section());
  }

  @Test
  void mapsNumericTypeToMustangIdAndSection() {
    String report =
        """
        <validation>
          <pdf>
            <messages>
              <error type="11">XMP Metadata: ConformanceLevel not found</error>
            </messages>
            <summary status="invalid"/>
          </pdf>
          <xml>
            <info>
              <profile>urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:en16931</profile>
            </info>
            <messages>
              <error type="18">schema validation fails</error>
              <warning type="4" location="/Seller/DefinedTradeContact" criterion="false">[PEPPOL-EN16931-R008] empty</warning>
            </messages>
            <summary status="invalid"/>
          </xml>
          <summary status="invalid"/>
        </validation>
        """;
    MustangResult result = engine.parseReport(report, false);
    assertEquals(DetectedFormat.FACTURX_EN16931, result.format());
    assertTrue(
        result.findings().stream()
            .anyMatch(f -> "MUSTANG_11".equals(f.id()) && CheckSection.PDFA.equals(f.section())));
    assertTrue(
        result.findings().stream()
            .anyMatch(f -> "MUSTANG_18".equals(f.id()) && CheckSection.SCHEMA.equals(f.section())));
    assertTrue(
        result.findings().stream()
            .anyMatch(
                f ->
                    "PEPPOL-EN16931-R008".equals(f.id())
                        && CheckSection.SCHEMATRON.equals(f.section())
                        && "/Seller/DefinedTradeContact".equals(f.location())));
  }

  @Test
  void xmpErrorsFillPdfaWithoutSyntheticDuplicate() {
    String report =
        """
        <validation>
          <pdf>
            ValidationResult [flavour=3b, totalAssertions=10, assertions=[], isCompliant=false]
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
    assertEquals(
        1,
        result.findings().stream()
            .filter(f -> f.isError() && CheckSection.PDFA.equals(f.section()))
            .count());
    assertEquals("MUSTANG_11", result.findings().get(0).id());
  }

  @Test
  void syntheticPdfaWhenCompliantFalseWithoutErrorNodes() {
    String report =
        """
        <validation>
          <pdf>
            ValidationResult [flavour=3b, totalAssertions=1, assertions=[], isCompliant=false]
            <summary status="invalid"/>
          </pdf>
          <xml>
            <info>
              <profile>urn:cen.eu:en16931:2017</profile>
            </info>
            <summary status="valid"/>
          </xml>
          <summary status="invalid"/>
        </validation>
        """;
    MustangResult result = engine.parseReport(report, false);
    assertTrue(result.findings().stream().anyMatch(f -> "MUSTANG_23".equals(f.id())));
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
    assertTrue(result.findings().stream().noneMatch(f -> "MUSTANG_23".equals(f.id())));
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
