# 0.3 JSON report contract (Summary + dual XML)

Breaking CLI JSON change for eRechnung 0.3: stdout is `verdict`, `summary` (IOK-analog sections + finding buckets), `erechnung`, and `mustang-pruefbericht`. Flat `errors`/`warnings`/`notices`/`metadata`/`erechnung_xml` are removed so clients have one structured surface; Mustang’s raw XML remains the home for veraPDF assertion dumps, and PDF/A is exposed only as a Mustang check.

## Section mapping (Mustang)

| Origin / type | Section |
|---|---|
| Messages under `<pdf>` | `pdfa` (default) |
| Messages under `<xml>` | `schematron` (default) |
| type `18` (schema validation) | `schema` |
| type `17` (attachment naming) | `embedded_xml` |
| type `4` (empty-element / PEPPOL-R008 style) | `schematron` |
| KoSIT schema / wellformed / processing | `schema` |
| KoSIT failed asserts | `schematron` |
| PDF/A fail without error nodes | synthetic `MUSTANG_23` → `pdfa` |

`metadata_embedding` is reserved (always present; currently rarely receives findings).
