package ai.kkc.erechnung.model;

import java.util.List;
import java.util.stream.Stream;

public record EngineFindings(
    DetectedFormat format, List<Finding> mustang, List<Finding> kosit) {

  public List<Finding> all() {
    return Stream.concat(mustang.stream(), kosit.stream()).toList();
  }

  public boolean hasErrors() {
    return all().stream().anyMatch(Finding::isError);
  }
}
