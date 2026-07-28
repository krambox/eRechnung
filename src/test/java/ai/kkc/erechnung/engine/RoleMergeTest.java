package ai.kkc.erechnung.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.kkc.erechnung.model.DetectedFormat;
import org.junit.jupiter.api.Test;

class RoleMergeTest {

  @Test
  void kositOnlyForXRechnung() {
    assertTrue(ValidationOrchestrator.shouldRunKosit(DetectedFormat.XRECHNUNG));
    assertFalse(ValidationOrchestrator.shouldRunKosit(DetectedFormat.FACTURX_EN16931));
    assertFalse(ValidationOrchestrator.shouldRunKosit(DetectedFormat.NOT_ERECHNUNG));
  }
}
