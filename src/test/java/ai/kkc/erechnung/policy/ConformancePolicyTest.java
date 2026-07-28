package ai.kkc.erechnung.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.kkc.erechnung.model.DetectedFormat;
import ai.kkc.erechnung.model.EngineFindings;
import ai.kkc.erechnung.model.Finding;
import ai.kkc.erechnung.model.Severity;
import ai.kkc.erechnung.model.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConformancePolicyTest {

  private final ConformancePolicy policy = new ConformancePolicy();

  @Test
  void conformantWhenEn16931AndNoErrors() {
    var findings = new EngineFindings(DetectedFormat.FACTURX_EN16931, List.of(), List.of());
    assertEquals(Verdict.CONFORMANT, policy.decide(findings));
    assertEquals(0, policy.exitCode(Verdict.CONFORMANT));
  }

  @Test
  void conformantWhenXRechnungAndNoErrorsDespiteWarnings() {
    var findings =
        new EngineFindings(
            DetectedFormat.XRECHNUNG,
            List.of(Finding.warning("mustang", "W1", "minor")),
            List.of(Finding.notice("kosit", "N1", "info")));
    assertEquals(Verdict.CONFORMANT, policy.decide(findings));
  }

  @Test
  void nonconformantWhenEngineReportsError() {
    var findings =
        new EngineFindings(
            DetectedFormat.XRECHNUNG,
            List.of(Finding.error("mustang", "E1", "missing BT-31")),
            List.of());
    assertEquals(Verdict.NONCONFORMANT, policy.decide(findings));
    assertEquals(1, policy.exitCode(Verdict.NONCONFORMANT));
  }

  @Test
  void notErechnungWhenFormatUnknown() {
    var findings = new EngineFindings(DetectedFormat.NOT_ERECHNUNG, List.of(), List.of());
    assertEquals(Verdict.NOT_ERECHNUNG, policy.decide(findings));
    assertEquals(2, policy.exitCode(Verdict.NOT_ERECHNUNG));
  }

  @Test
  void nonconformantWhenKositErrorEvenIfMustangClean() {
    var findings =
        new EngineFindings(
            DetectedFormat.XRECHNUNG,
            List.of(),
            List.of(Finding.error("kosit", "BR-DE-1", "seller VAT missing")));
    assertEquals(Verdict.NONCONFORMANT, policy.decide(findings));
  }

  @Test
  void toolErrorExitCodeIsThree() {
    assertEquals(3, policy.exitCode(Verdict.TOOL_ERROR));
  }
}
