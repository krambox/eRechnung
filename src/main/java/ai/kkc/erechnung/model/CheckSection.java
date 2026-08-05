package ai.kkc.erechnung.model;

import java.util.List;

/** Fixed Summary check-section keys (IOK-analog). */
public final class CheckSection {

  public static final String SCHEMA = "schema";
  public static final String SCHEMATRON = "schematron";
  public static final String PDFA = "pdfa";
  public static final String EMBEDDED_XML = "embedded_xml";
  public static final String METADATA_EMBEDDING = "metadata_embedding";

  public static final List<String> ALL =
      List.of(SCHEMA, SCHEMATRON, PDFA, EMBEDDED_XML, METADATA_EMBEDDING);

  private CheckSection() {}
}
