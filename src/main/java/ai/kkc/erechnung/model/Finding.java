package ai.kkc.erechnung.model;

public record Finding(Severity severity, String engine, String id, String message) {

  public static Finding error(String engine, String id, String message) {
    return new Finding(Severity.ERROR, engine, id, message);
  }

  public static Finding warning(String engine, String id, String message) {
    return new Finding(Severity.WARNING, engine, id, message);
  }

  public static Finding notice(String engine, String id, String message) {
    return new Finding(Severity.NOTICE, engine, id, message);
  }

  public boolean isError() {
    return severity == Severity.ERROR;
  }
}
