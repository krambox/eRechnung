package ai.kkc.erechnung.policy;

import ai.kkc.erechnung.model.DetectedFormat;
import ai.kkc.erechnung.model.EngineFindings;
import ai.kkc.erechnung.model.Verdict;

/**
 * Maps engine findings and detected format to the three-valued conformance verdict.
 *
 * <p>Policy: engine errors fail; warnings/notices do not. Format must be XRechnung or
 * Factur-X/ZUGFeRD EN 16931.
 */
public final class ConformancePolicy {

  public Verdict decide(EngineFindings findings) {
    if (findings.format() == DetectedFormat.NOT_ERECHNUNG) {
      return Verdict.NOT_ERECHNUNG;
    }
    if (findings.hasErrors()) {
      return Verdict.NONCONFORMANT;
    }
    return Verdict.CONFORMANT;
  }

  public int exitCode(Verdict verdict) {
    return switch (verdict) {
      case CONFORMANT -> 0;
      case NONCONFORMANT -> 1;
      case NOT_ERECHNUNG -> 2;
      case TOOL_ERROR -> 3;
    };
  }
}
