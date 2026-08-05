package ai.kkc.erechnung.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Final report for one validate invocation (JSON contract 0.3). */
public final class ValidationReport {

  private Verdict verdict;
  private List<Finding> errors = new ArrayList<>();
  private List<Finding> warnings = new ArrayList<>();
  private List<Finding> notices = new ArrayList<>();
  private Map<String, Object> summary = new LinkedHashMap<>();
  private String erechnungXml = "";
  private String mustangPruefbericht = "";

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

  public Map<String, Object> getSummary() {
    return summary;
  }

  public void setSummary(Map<String, Object> summary) {
    this.summary = summary == null ? new LinkedHashMap<>() : summary;
  }

  public String getErechnungXml() {
    return erechnungXml;
  }

  public void setErechnungXml(String erechnungXml) {
    this.erechnungXml = erechnungXml == null ? "" : erechnungXml;
  }

  public String getMustangPruefbericht() {
    return mustangPruefbericht;
  }

  public void setMustangPruefbericht(String mustangPruefbericht) {
    this.mustangPruefbericht = mustangPruefbericht == null ? "" : mustangPruefbericht;
  }

  public void addFinding(Finding finding) {
    switch (finding.severity()) {
      case ERROR -> errors.add(finding);
      case WARNING -> warnings.add(finding);
      case NOTICE -> notices.add(finding);
    }
  }
}
