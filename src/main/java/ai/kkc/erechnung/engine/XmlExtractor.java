package ai.kkc.erechnung.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.mustangproject.ZUGFeRD.ZUGFeRDImporter;

/** Extracts E-Rechnungs-XML from a PDF hybrid or returns XML file bytes as text. */
public final class XmlExtractor {

  public String extract(Path path) throws IOException {
    byte[] bytes = Files.readAllBytes(path);
    return extract(bytes, path.getFileName().toString());
  }

  public String extract(byte[] bytes, String filename) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    if (isPdf(bytes)) {
      try {
        ZUGFeRDImporter importer = new ZUGFeRDImporter(new java.io.ByteArrayInputStream(bytes));
        byte[] raw = importer.getRawXML();
        if (raw == null || raw.length == 0) {
          return "";
        }
        return new String(raw, StandardCharsets.UTF_8);
      } catch (Exception ex) {
        return "";
      }
    }
    // Strip UTF-8 BOM if present
    int offset = 0;
    if (bytes.length >= 3
        && (bytes[0] & 0xFF) == 0xEF
        && (bytes[1] & 0xFF) == 0xBB
        && (bytes[2] & 0xFF) == 0xBF) {
      offset = 3;
    }
    return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
  }

  private static boolean isPdf(byte[] bytes) {
    return bytes.length >= 4
        && bytes[0] == '%'
        && bytes[1] == 'P'
        && bytes[2] == 'D'
        && bytes[3] == 'F';
  }
}
