# Security Policy

EV Cockpit is an independently maintained GPL-3.0 Android project. It is still under development. No historical APK or GitHub Release should be assumed to have been verified on physical hardware or signed with a trusted owner certificate.

## Supported sources

| Branch or artifact | Security status |
| --- | --- |
| main | Current integration branch; fixes are reviewed, not a guarantee of production support |
| feature/ev-cockpit | Active development; testing and security review required |
| Historical source ZIP archives and old APKs | Unreviewed/unsupported; may contain obsolete credentials |

The nativecockpit Gradle module is currently present on feature/ev-cockpit, not main. Signing must remain on hold until the prior credential exposure has been investigated and owner certificate continuity established.

## Report a vulnerability privately

Please **do not open a public Issue or PR containing a vulnerability exploit, access token, API key, signing password, private keystore or personal vehicle data**.

If GitHub Private Vulnerability Reporting is enabled, open this repository's **Security → Advisories → Report a vulnerability** option. If the option is unavailable, request that the maintainer enable private vulnerability reporting using a public, non-sensitive request; **do not include the vulnerability details or secrets in that request**. The maintainer must arrange a confidential reporting channel before sensitive details are exchanged. No private reporting email address is asserted here.

A useful confidential report includes the affected commit, reproducible impact, steps that avoid revealing real credentials, and a proposed mitigation. Do not test against vehicles or accounts without permission. Never attempt state-changing OBD/CAN commands; this project is intended only for read-only ELM327 Mode 01 and UDS 0x22 operations.

## Response and remediation

The maintainer will triage reported issues as capacity permits. No fixed response or patch SLA is promised. Security fixes and release artifacts require code review and CI validation, followed by real-device acceptance testing where applicable. For suspected credential exposure: revoke/rotate via the issuer first, inspect historical releases, and document certificate migration before signing any new production APK.

See docs/SECRET_ROTATION_PLAYBOOK.md and docs/PROTECTED_RELEASE_SETUP.md for the current incident-response and signing hold procedures.

## Dependency and CI policy

Dependencies are reviewed through Dependabot PRs for Gradle and GitHub Actions. CODEOWNERS by itself does not prevent merges: enable protected branch rules and required status checks, and do not expose production secrets to pull requests. No production signing is permitted on untrusted code.
