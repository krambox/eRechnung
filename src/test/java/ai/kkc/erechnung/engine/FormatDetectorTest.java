package ai.kkc.erechnung.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.kkc.erechnung.model.DetectedFormat;
import org.junit.jupiter.api.Test;

class FormatDetectorTest {

  private final FormatDetector detector = new FormatDetector();

  @Test
  void detectsXRechnung() {
    assertEquals(
        DetectedFormat.XRECHNUNG,
        detector.fromProfile(
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0"));
  }

  @Test
  void detectsFacturXEn16931() {
    assertEquals(
        DetectedFormat.FACTURX_EN16931,
        detector.fromProfile("urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:en16931"));
  }

  @Test
  void detectsExtendedAsEn16931Family() {
    assertEquals(
        DetectedFormat.FACTURX_EN16931,
        detector.fromProfile("urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended"));
  }

  @Test
  void rejectsMinimum() {
    assertEquals(
        DetectedFormat.NOT_ERECHNUNG,
        detector.fromProfile("urn:factur-x.eu:1p0:minimum"));
  }

  @Test
  void rejectsUnrelatedCiusSubstring() {
    assertEquals(DetectedFormat.NOT_ERECHNUNG, detector.fromProfile("my-cius-app-profile"));
  }

  @Test
  void acceptsZugferdCiusUri() {
    assertEquals(
        DetectedFormat.FACTURX_EN16931,
        detector.fromProfile("urn:cen.eu:en16931:2017#compliant#urn:zugferd.de:2p0:cius"));
  }

  @Test
  void rejectsNull() {
    assertEquals(DetectedFormat.NOT_ERECHNUNG, detector.fromProfile(null));
  }
}
