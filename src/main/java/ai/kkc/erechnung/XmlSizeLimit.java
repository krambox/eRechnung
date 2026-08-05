package ai.kkc.erechnung;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses human size strings for the invoice-XML byte limit (default 5 MiB). */
public final class XmlSizeLimit {

  public static final long DEFAULT_BYTES = 5L * 1024 * 1024;

  private static final Pattern SPEC =
      Pattern.compile("^\\s*(\\d+)\\s*(b|k|kb|ki|kib|m|mb|mi|mib)?\\s*$", Pattern.CASE_INSENSITIVE);

  private XmlSizeLimit() {}

  /**
   * @param spec e.g. {@code 5MiB}, {@code 5120KiB}, {@code 5242880}; blank → default
   */
  public static long parseBytes(String spec) {
    if (spec == null || spec.isBlank()) {
      return DEFAULT_BYTES;
    }
    Matcher m = SPEC.matcher(spec.trim());
    if (!m.matches()) {
      throw new IllegalArgumentException(
          "invalid --max-xml-size '" + spec + "' (examples: 5MiB, 512KiB, 1048576)");
    }
    long n = Long.parseLong(m.group(1));
    String unit = m.group(2) == null ? "b" : m.group(2).toLowerCase(Locale.ROOT);
    return switch (unit) {
      case "b" -> n;
      case "k", "kb", "ki", "kib" -> Math.multiplyExact(n, 1024L);
      case "m", "mb", "mi", "mib" -> Math.multiplyExact(n, 1024L * 1024L);
      default -> throw new IllegalArgumentException("invalid size unit in '" + spec + "'");
    };
  }

  public static String formatMiB(long bytes) {
    if (bytes % (1024L * 1024L) == 0) {
      return (bytes / (1024L * 1024L)) + "MiB";
    }
    if (bytes % 1024L == 0) {
      return (bytes / 1024L) + "KiB";
    }
    return bytes + "B";
  }
}
