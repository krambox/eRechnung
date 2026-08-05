# eRechnung

CLI for German B2B e-invoice conformance: technical policy label over Mustang (+ conditional KoSIT), not legal advice.

## Language

**Verdict**:
The three-valued policy outcome of one validate run: `conformant`, `nonconformant`, or `not_erechnung` (plus `tool_error` for tool/IO failure).
_Avoid_: result, status (alone), pass/fail

**Summary**:
The structured, UI-analog validation report for one run. It replaces the former flat `errors` / `warnings` / `notices` arrays and absorbs former `metadata` as the sole structured surface beside `verdict` and the two XML fields. Top-level JSON keys: `verdict`, `summary`, `erechnung`, `mustang-pruefbericht`; keys inside `summary` use English snake_case. Summary header includes `filename`, `format`, `profile`, `generation`, `engines` (`mustang` / `kosit` only), `duration_ms`, `validator`, and `attachment` when applicable — not UI copy strings derived from `verdict`. veraPDF assertion detail lives only inside the Mustang-Prüfbericht; Summary must not expose veraPDF as a separate engine.
_Avoid_: findings list (as top-level JSON), flat finding arrays, top-level metadata, Prüfbericht (for this object — that name is reserved for the Mustang raw XML), German/kebab keys inside summary, headline/result_label copy fields, pdfa.engine=verapdf in the public contract

**E-Rechnung XML**:
The extracted or standalone invoice XML payload included in the JSON under the field `erechnung`.
_Avoid_: erechnung_xml, invoice_xml, payload (alone)

**Mustang-Prüfbericht**:
Mustang’s raw XML validation report (`<validation>…</validation>`), included in the JSON under the field `mustang-pruefbericht` (kebab-case top-level key by contract). Sole home for veraPDF assertion dumps when present.
_Avoid_: mustang_report, mustang_pruefbericht (snake), raw report (alone), ValidationResult (veraPDF dump), duplicating veraPDF assertions in Summary

**Section status**:
Per-check status inside Summary: `ok`, `error`, `warning`, or `notice`. `error` means the section contributes to rejecting the invoice (`verdict` `nonconformant`); `warning` and `notice` do not. A section’s status is the worst severity among its findings; no findings → `ok`.
_Avoid_: info (use notice), fail/pass, invalid/valid (alone), kritisch

**Check section**:
One of five fixed Summary checks: `schema`, `schematron`, `pdfa`, `embedded_xml`, `metadata_embedding`. Always present. When a check does not apply (e.g. pure XML input and no PDF/A), `applicable` is `false` and status is `ok` without findings. Finding→section assignment follows a fixed Mustang-type / origin mapping table (documented with the JSON contract). PDF/A is exposed only as a Mustang check (veraPDF runs inside Mustang and must not appear as a separate engine in the JSON contract). If Mustang reports PDF/A failure without discrete error nodes, a gap-fill Finding may be added with `engine: mustang` and `section: pdfa`.
_Avoid_: optional section keys, absent (as status), IOK German labels as JSON keys, exposing veraPDF as engine or checker beside Mustang

**Finding**:
One validation message in Summary, listed only in severity buckets `errors` / `warnings` / `notices` (not nested under sections). Each Finding carries `engine` (`mustang` | `kosit`), `section`, `id`, `message`, and optional `location` (omit or null when absent). `id` is `MUSTANG_<type>` when Mustang’s numeric `type` is the best handle; otherwise a rule id from the message. `message` is the engine’s raw text. Section assignment follows the fixed type/origin table. Never use `criterion="false"` as id; never invent IOK paraphrase titles; never set `engine` to veraPDF.
_Avoid_: raw type alone as primary id when MUSTANG_N applies, criterion boolean strings as ids, ad-hoc section guessing, XMP errors under `metadata_embedding` when mapped to `pdfa`, duplicating findings under sections, flat top-level finding arrays, paraphrased German titles, dropping engine on findings
