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

Artifact: `target/erechnung-0.2.0.jar` (shaded Fat-JAR). Releases: attach the same JAR from GitHub Releases.

## Usage

```bash
java -jar target/erechnung-0.2.0.jar validate path/to/invoice.pdf
java -jar target/erechnung-0.2.0.jar validate path/to/invoice.xml
```

JSON on **stdout only** (always includes full `erechnung_xml`). No sidecar files.

### Verdict & exit codes

| Verdict | Exit | Meaning |
|---------|------|---------|
| `conformant` | 0 | XR or FX/ZF EN16931 with no engine errors |
| `nonconformant` | 1 | Recognized format, but validation errors |
| `not_erechnung` | 2 | Not an accepted e-invoice format |
| `tool_error` | 3 | Missing file, bad args, internal failure |

### JSON shape

```json
{
  "verdict": "conformant",
  "errors": [],
  "warnings": [],
  "notices": [{"engine": "mustang", "id": "…", "message": "…", "severity": "notice"}],
  "metadata": {
    "filename": "invoice.xml",
    "format": "xrechnung",
    "profile": "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0",
    "engines": {"mustang": true, "kosit": true},
    "pdfa": {"status": "absent", "engine": "verapdf"}
  },
  "erechnung_xml": "…"
}
```

For PDF hybrids, `metadata.pdfa` is `conformant` / `nonconformant` plus veraPDF `flavour` (e.g. `3b`), `total_assertions`, and failed `assertions` (`clause`, `test`, `message`, `location`, …). PDF/A failures also appear as a `mustang`/`pdfa` error when Mustang does not emit a discrete section-23 finding.

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

Third-party: Mustang (Apache-2.0), KoSIT Validator (Apache-2.0), veraPDF (MPL-2.0), EN16931 / XRechnung artefacts (often EUPL-2.0). See NOTICE.
