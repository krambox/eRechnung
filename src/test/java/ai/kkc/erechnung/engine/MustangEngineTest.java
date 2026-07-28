package ai.kkc.erechnung.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.kkc.erechnung.model.DetectedFormat;
import ai.kkc.erechnung.model.Severity;
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
  }
}
