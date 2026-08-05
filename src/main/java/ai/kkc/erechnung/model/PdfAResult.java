package ai.kkc.erechnung.model;

/** PDF/A status from Mustang’s report (absent for standalone XML). Internal only. */
public record PdfAResult(String status, String flavour) {

  public static final String ABSENT = "absent";
  public static final String CONFORMANT = "conformant";
  public static final String NONCONFORMANT = "nonconformant";

  public static PdfAResult absent() {
    return new PdfAResult(ABSENT, null);
  }

  public static PdfAResult of(String status, String flavour) {
    return new PdfAResult(status, flavour);
  }
}
