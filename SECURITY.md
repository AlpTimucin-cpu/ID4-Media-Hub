# Security Policy

## Scope and supported code

EV Cockpit is an independently maintained GPL-3.0 Android project.
The default branch currently contains the Android media hub at :app.
The native Kotlin/Compose cockpit is maintained separately on feature/ev-cockpit; it is not yet merged into main.
Security-sensitive changes are staged in draft pull requests and are NOT production-approved releases.

Report security issues affecting current source, signing/release workflows,
GitHub Actions supply chain, API keys, data handling, Android Auto permissions
or **read-only** Bluetooth / ELM327 diagnostics.

## Private vulnerability reporting

**Do not post proof-of-concept exploits, credentials, vehicle identifiers,
keystore material, API keys, customer data or full vulnerable traces in a public issue.**

Preferred: use GitHub's private vulnerability reporting if enabled:
https://github.com/AlpTimucin-cpu/ev-cockpit/security/advisories/new

If GitHub does not offer private reporting for this repository, ask the maintainer
through their GitHub profile for a secure contact channel, **without posting
the vulnerability details publicly**. Do not assume this repository has a
published security email address.

For ordinary non-security bugs, use GitHub Issues and remove personal information
from logs and screenshots.

## What a useful private report includes

Describe affected branch and commit, environment, clear reproduction steps,
potential impact, and a minimal redacted proof of concept. Report whether any
data is collected from a real vehicle; do not include VIN, live bearer tokens,
passwords or raw secret bytes.

The maintainer will triage, establish an appropriate confidential response plan,
and coordinate fixes and advisory information. No response or patch deadline is
promised without an agreed maintenance SLA.

## Known holds and hard boundaries

- The old TomTom key and an older signing bundle may have been exposed in
  repository history. Existing artifacts/releases must not be assumed clean.
- Production signing remains on hold until the legitimate owner verifies
  certificate identity, continuity and appropriate protected release approvals.
- All OBD communication should remain **read-only**: Mod 01 and UDS 0x22.
  Never include diagnostic service write routines or fabricated battery values.
- GitHub Actions release workflows must not receive credentials on untrusted
  pull requests and must not upload private signing keys as artifacts.
- Public Git history is not cleaned by deleting a file or updating .gitignore.

Read the guidance in docs/SECRET_ROTATION_PLAYBOOK.md and
docs/PROTECTED_RELEASE_SETUP.md (after the relevant PRs merge).
