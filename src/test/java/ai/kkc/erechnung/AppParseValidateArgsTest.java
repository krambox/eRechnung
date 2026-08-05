package ai.kkc.erechnung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AppParseValidateArgsTest {

  @Test
  void defaultsMaxXmlSize() {
    var p = App.parseValidateArgs(new String[] {"validate", "a.pdf"});
    assertEquals("a.pdf", p.path());
    assertEquals(XmlSizeLimit.DEFAULT_BYTES, p.maxXmlBytes());
    assertEquals(false, p.serve());
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

  @Test
  void serveDefaults() {
    var p = App.parseValidateArgs(new String[] {"validate", "--serve"});
    assertTrue(p.serve());
    assertNull(p.path());
    assertEquals("127.0.0.1", p.bind());
    assertEquals(8092, p.port());
    assertEquals(Duration.ofSeconds(20), p.queueWait());
    assertEquals(XmlSizeLimit.DEFAULT_BYTES, p.maxXmlBytes());
  }

  @Test
  void serveOptions() {
    var p =
        App.parseValidateArgs(
            new String[] {
              "validate",
              "--serve",
              "--port=18092",
              "--bind=127.0.0.1",
              "--queue-wait=20s",
              "--max-xml-size=5MiB"
            });
    assertTrue(p.serve());
    assertEquals(18092, p.port());
    assertEquals(Duration.ofSeconds(20), p.queueWait());
  }

  @Test
  void serveRejectsPath() {
    assertThrows(
        IllegalArgumentException.class,
        () -> App.parseValidateArgs(new String[] {"validate", "--serve", "a.pdf"}));
  }

  @Test
  void extractsFilePart() {
    String boundary = "----testboundary";
    byte[] body =
        ValidateServe.buildMultipart(
            boundary, "inv.xml", "<Invoice/>".getBytes(StandardCharsets.UTF_8));
    var part = ValidateServe.extractFilePart(body, boundary);
    assertNotNull(part);
    assertEquals("inv.xml", part.filename());
    assertEquals("<Invoice/>", new String(part.bytes(), StandardCharsets.UTF_8));
  }

  @Test
  void parsesBoundary() {
    assertEquals("abc", ValidateServe.multipartBoundary("multipart/form-data; boundary=abc"));
    assertEquals("abc", ValidateServe.multipartBoundary("multipart/form-data; boundary=\"abc\""));
  }
}
