package ai.kkc.erechnung.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Final JSON report for one validate invocation. */
public final class ValidationReport {

  private Verdict verdict;
  private List<Finding> errors = new ArrayList<>();
  private List<Finding> warnings = new ArrayList<>();
  private List<Finding> notices = new ArrayList<>();
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private String erechnungXml = "";

  public Verdict getVerdict() {
    return verdict;
  }

  public void setVerdict(Verdict verdict) {
    this.verdict = verdict;
  }

  public List<Finding> getErrors() {
    return errors;
  }

  public void setErrors(List<Finding> errors) {
    this.errors = errors;
  }

  public List<Finding> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<Finding> warnings) {
    this.warnings = warnings;
  }

  public List<Finding> getNotices() {
    return notices;
  }

  public void setNotices(List<Finding> notices) {
    this.notices = notices;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  public String getErechnungXml() {
    return erechnungXml;
  }

  public void setErechnungXml(String erechnungXml) {
    this.erechnungXml = erechnungXml == null ? "" : erechnungXml;
  }

  public void addFinding(Finding finding) {
    switch (finding.severity()) {
      case ERROR -> errors.add(finding);
      case WARNING -> warnings.add(finding);
      case NOTICE -> notices.add(finding);
    }
  }
}
