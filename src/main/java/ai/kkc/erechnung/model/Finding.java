package ai.kkc.erechnung.model;

/** One validation message attributed to an engine and a check section. */
public record Finding(
    Severity severity, String engine, String id, String message, String section, String location) {

  public static Finding error(String engine, String id, String message) {
    return error(engine, id, message, null, null);
  }

  public static Finding error(
      String engine, String id, String message, String section, String location) {
    return new Finding(Severity.ERROR, engine, id, message, section, location);
  }

  public static Finding warning(String engine, String id, String message) {
    return warning(engine, id, message, null, null);
  }

  public static Finding warning(
      String engine, String id, String message, String section, String location) {
    return new Finding(Severity.WARNING, engine, id, message, section, location);
  }

  public static Finding notice(String engine, String id, String message) {
    return notice(engine, id, message, null, null);
  }

  public static Finding notice(
      String engine, String id, String message, String section, String location) {
    return new Finding(Severity.NOTICE, engine, id, message, section, location);
  }

  public boolean isError() {
    return severity == Severity.ERROR;
  }
}
