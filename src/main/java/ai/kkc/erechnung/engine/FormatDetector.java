package ai.kkc.erechnung.engine;

import ai.kkc.erechnung.model.DetectedFormat;

/**
 * Maps Mustang/guideline profile URIs to formats accepted under the DE B2B e-invoice mandate.
 *
 * <p>Accepts XRechnung and Factur-X/ZUGFeRD EN 16931 (incl. Extended). Rejects Minimum/Basic-only
 * profiles and unrecognized content as {@link DetectedFormat#NOT_ERECHNUNG}.
 */
public final class FormatDetector {

  public DetectedFormat fromProfile(String profile) {
    if (profile == null || profile.isBlank()) {
      return DetectedFormat.NOT_ERECHNUNG;
    }
    String p = profile.trim().toLowerCase();
    if (p.contains("xrechnung")) {
      return DetectedFormat.XRECHNUNG;
    }
    // Extended is EN16931-conformant+
    if (p.contains("extended")) {
      return DetectedFormat.FACTURX_EN16931;
    }
    // Explicit EN16931 / Factur-X Comfort (and ZUGFeRD CIUS synonym for EN16931)
    if (p.contains("urn:cen.eu:en16931:2017")
        || p.contains(":en16931")
        || p.contains("comfort")
        || p.contains(":cius")
        || p.endsWith("cius")) {
      if (isBasicOrMinimumOnly(p)) {
        return DetectedFormat.NOT_ERECHNUNG;
      }
      return DetectedFormat.FACTURX_EN16931;
    }
    if (isBasicOrMinimumOnly(p)) {
      return DetectedFormat.NOT_ERECHNUNG;
    }
    return DetectedFormat.NOT_ERECHNUNG;
  }

  private static boolean isBasicOrMinimumOnly(String p) {
    boolean basicFamily =
        p.contains("basicwl")
            || p.contains("basic")
            || p.contains("minimum")
            || p.contains(":min");
    boolean en16931 =
        p.contains("en16931") || p.contains("comfort") || p.contains("xrechnung") || p.contains("extended");
    return basicFamily && !en16931;
  }
}
