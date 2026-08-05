package ai.kkc.erechnung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AppParseValidateArgsTest {

  @Test
  void defaultsMaxXmlSize() {
    var p = App.parseValidateArgs(new String[] {"validate", "a.pdf"});
    assertEquals("a.pdf", p.path());
    assertEquals(XmlSizeLimit.DEFAULT_BYTES, p.maxXmlBytes());
  }

  @Test
  void acceptsEqualsAndSeparateForms() {
    assertEquals(
        10L * 1024 * 1024,
        App.parseValidateArgs(new String[] {"validate", "--max-xml-size=10MiB", "x.xml"})
            .maxXmlBytes());
    assertEquals(
        1024,
        App.parseValidateArgs(new String[] {"validate", "--max-xml-size", "1KiB", "x.xml"})
            .maxXmlBytes());
  }

  @Test
  void rejectsUnknownOption() {
    assertThrows(
        IllegalArgumentException.class,
        () -> App.parseValidateArgs(new String[] {"validate", "--nope", "a.pdf"}));
  }
}
