# eRechnung

Fat-JAR CLI for **E-Rechnungs-Konformitätsprüfung**: is this PDF/XML an e-invoice suitable under the German B2B e-invoice mandate?

Not legal advice. “Conformant” is a **technical policy label** over Mustang (+ conditional KoSIT) validation — not a Rechtsgutachten or booking decision.

## What it checks

| Accepted formats | XRechnung **or** Factur-X/ZUGFeRD **EN 16931** (incl. PDF/A-3 hybrid). Extended counts as EN16931-family. |
| Not an e-invoice | Image-only PDF, unrelated XML, Minimum/Basic-only profiles → `not_erechnung` |
| Engines | **Mustang** always (FX/ZF + PDF/A via embedded veraPDF). **KoSIT** only when an XRechnung profile is detected (**role merge** — KoSIT rules are not applied to plain Factur-X EN16931). |
| Policy | Engine **errors** fail; warnings/notices do not. |

Distinct from the light Python **E-Rechnungs-Prüfung** in [officetools](https://github.com/krambox/officetools).

## Build

Requires **JDK 17+** and Maven 3.9+.

```bash
mvn -q package
```

Artifact: `target/erechnung-0.3.0.jar` (shaded Fat-JAR). Releases: attach the same JAR from GitHub Releases.

## Usage

```bash
java -jar target/erechnung-0.3.0.jar validate path/to/invoice.pdf
java -jar target/erechnung-0.3.0.jar validate path/to/invoice.xml
```

JSON on **stdout only**. No sidecar files.

### Verdict & exit codes

| Verdict | Exit | Meaning |
|---------|------|---------|
| `conformant` | 0 | XR or FX/ZF EN16931 with no engine errors |
| `nonconformant` | 1 | Recognized format, but validation errors |
| `not_erechnung` | 2 | Not an accepted e-invoice format |
| `tool_error` | 3 | Missing file, bad args, internal failure |

### JSON shape (0.3)

```json
{
  "verdict": "nonconformant",
  "summary": {
    "filename": "invoice.pdf",
    "duration_ms": 200,
    "format": "facturx_en16931",
    "generation": "2",
    "profile": "urn:cen.eu:en16931:2017",
    "attachment": "factur-x.xml",
    "validator": { "mustang": "2.24.0" },
    "engines": { "mustang": true, "kosit": false },
    "sections": {
      "schema": { "applicable": true, "status": "ok" },
      "schematron": { "applicable": true, "status": "warning" },
      "pdfa": { "applicable": true, "status": "error" },
      "embedded_xml": { "applicable": true, "status": "ok" },
      "metadata_embedding": { "applicable": true, "status": "ok" }
    },
    "errors": [
      {
        "engine": "mustang",
        "section": "pdfa",
        "id": "MUSTANG_11",
        "message": "XMP Metadata: ConformanceLevel not found",
        "location": null
      }
    ],
    "warnings": [],
    "notices": []
  },
  "erechnung": "…",
  "mustang-pruefbericht": "<validation>…</validation>"
}
```

`erechnung` is the invoice XML; `mustang-pruefbericht` is Mustang’s raw report (incl. veraPDF dumps). Section status is `ok` | `error` | `warning` | `notice` (worst finding in that section). Pure XML input marks `pdfa` / `embedded_xml` / `metadata_embedding` as `applicable: false`.

## Corpus tests

The [ZUGFeRD/corpus](https://github.com/ZUGFeRD/corpus) submodule under `corpus/` holds labeled PDF/XML samples (`correct` / `fail` / `valid`, plus `XML-Rechnung`).

```bash
git submodule update --init
mvn -Pcorpus test
```

        Default `mvn test` skips `@Tag("corpus")` (slow). Expectations: `fail` → not conformant; `correct`/`valid` EN16931/Extended/XRechnung → `conformant`; BASIC/MINIMUM and foreign formats → `not_erechnung`. Known Mustang↔corpus drifts are listed in `src/test/resources/corpus-expectation-overrides.tsv`.

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

Third-party: Mustang (Apache-2.0), KoSIT Validator (Apache-2.0), veraPDF (MPL-2.0), EN16931 / XRechnung artefacts (often EUPL-2.0). See NOTICE.
