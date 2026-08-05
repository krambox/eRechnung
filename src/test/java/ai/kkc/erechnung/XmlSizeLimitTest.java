package ai.kkc.erechnung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class XmlSizeLimitTest {

  @Test
  void defaultIsFiveMib() {
    assertEquals(5L * 1024 * 1024, XmlSizeLimit.DEFAULT_BYTES);
    assertEquals(XmlSizeLimit.DEFAULT_BYTES, XmlSizeLimit.parseBytes(null));
    assertEquals(XmlSizeLimit.DEFAULT_BYTES, XmlSizeLimit.parseBytes(""));
  }

  @Test
  void parsesMibAndBytes() {
    assertEquals(5L * 1024 * 1024, XmlSizeLimit.parseBytes("5MiB"));
    assertEquals(5L * 1024 * 1024, XmlSizeLimit.parseBytes("5mi"));
    assertEquals(1024, XmlSizeLimit.parseBytes("1KiB"));
    assertEquals(100, XmlSizeLimit.parseBytes("100"));
    assertEquals(100, XmlSizeLimit.parseBytes("100B"));
  }

  @Test
  void rejectsGarbage() {
    assertThrows(IllegalArgumentException.class, () -> XmlSizeLimit.parseBytes("five"));
  }
}
