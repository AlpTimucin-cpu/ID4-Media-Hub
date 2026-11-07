# EV Cockpit — TomTom and signing material incident response

Checked: 7 November 2026. **This PR does not revoke or rotate any key.** Historical credentials should be considered potentially compromised until the provider/account owner confirms revocation.

## TomTom key: owner checklist

1. Open the original TomTom Developer account, identify the old ID4_TOMTOM_API_KEY by its label/identifier, and **revoke or disable** it. Never copy its value into GitHub, logs, this document, or chat.
2. Review historical requests, abusive spikes, API permissions, quotas, bills and usage alerts from the earliest suspected exposure. Keep evidence privately.
3. If needed, create a replacement within TomTom's trusted console and apply API, usage, budget and application restrictions supported by the provider.
4. Do not supply the replacement through GitHub Actions, BuildConfig or APK assets. Prefer each user's own key, entered after installation and protected with Android Keystore-backed storage. API requests may still expose a client-held key at runtime.
5. Check old public APKs, GitHub Releases, Actions artifacts and traffic configuration assets; preserve forensic copies privately, remove compromised public downloads where possible and inform affected testers.
6. Delete or disable the obsolete repository Actions secret in Settings → Secrets and variables → Actions. Never log or decode it for troubleshooting.
7. Verify in the provider dashboard that the old key no longer works. Document **date and reviewer only**, not the actual credential.

## Separate signing identity incident

The name EYA_GitHub_TEK_SECRET.txt was tracked historically on the development branch, potentially exposing a base64-encoded JKS bundle. Removing the current file does not remove prior Git commits.

- Treat the former signing identity as untrusted until its exposure and historic APK certificate fingerprints are privately investigated.
- Distinguish a Play App Signing **upload key** from the certificate used to directly update sideloaded APK installations; replacing one certificate may make direct in-place updates impossible.
- Do **not** create new permanent signing keys in GitHub Actions or upload key material/passwords as artifacts.
- Keep production signing/releases suspended until a protected Environment, manual reviewers, secure backup and a verified key continuity/migration plan are agreed.

## One-time whole-Git-history scan

Run a full history scan with all refs on a trusted clone:

~~~bash
git fetch --all --tags --prune
gitleaks git . --log-opts="--all" --redact=100 --no-banner --exit-code=1
~~~

Check the local Gitleaks version's help if its command name differs. The new CI workflow scans incremental PR/push changes; **manual workflow_dispatch full_history=true** scans all reachable refs, which may intentionally fail due to legacy exposure.

GitHub UI: Actions → EV Cockpit - Gitleaks secret scanning → Run workflow → full_history=true.

Gitleaks v8.30.1 is executed from a digest-pinned official GHCR image. The CI workflow does not upload a findings report or echo secret values. A secret finding fails the job.

Gitleaks cannot reliably detect every encoded JKS or custom token. Inspect historical *file names* as an additional review, without printing secret contents:

~~~bash
git log --all --format='%h %ad %s' --date=short -- EYA_GitHub_TEK_SECRET.txt
~~~

Also privately inventory historic GitHub Releases and artifacts. Revoke/rotate first. Rewriting public Git history is disruptive: obtain backups and approval before a reviewed history migration; do not casually force-push.

## APK integrity and key-leak check

The script scripts/check_apk_secrets.py unzips every APK entry in memory, scanning decompressed DEX, assets and resources for hardcoded private key signatures, sensitive filenames, nonempty TomTom configuration fields and token-shaped URLs. No matching secret bytes or filenames are logged. Empty or absent traffic-config.js files are allowed, **nonempty TomTom keys are blocked**.

Local check after a successful build:

~~~bash
python3 -m unittest discover -s tests -p 'test_apk_secret_scan.py'
python3 scripts/check_apk_secrets.py app/build/outputs/apk/debug/app-debug.apk
~~~

In Android CI, an APK safety failure blocks APK artifact upload and fails the job. The separate existing Manifest lint failure still makes the overall CI run fail even when test and APK steps pass.

These checks are heuristic and do not certify that an APK contains no secrets. Old APKs must be audited separately; newer checks cannot retroactively sanitize them.
