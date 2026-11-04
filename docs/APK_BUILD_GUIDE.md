# EV Cockpit — APK Build Guide

## Mobile workflow

Use the repository's **Actions** tab in your mobile browser (enable Desktop site if necessary).

1. Open `Native Cockpit · Kotlin Compose APK` in Actions and select **Run workflow**.
2. Choose branch `feature/ev-cockpit` and start the run.
3. The **build** job runs JVM tests, checks real GPS/OBD inputs, validates R8, and publishes a debug APK.
4. Download `EV-Cockpit-Native-Kotlin-Debug-APK` from **Artifacts** after a green build. Extract its ZIP and install the debug APK only for testing.
5. For a production APK, create repository Actions secret `EYA_SIGNING_BUNDLE_BASE64` with the existing release keystore. Never upload the keystore or passwords into source control.
6. Only after the release job passes, download `EV-Cockpit-Signed-Stable-Release` from **Artifacts**. Extract `app-release.apk` and install it on the phone.
7. Android may require permission for your browser or file manager to install an APK. Confirm the publisher and signing identity before updating an existing installation.

### Signing bundle schema

The repository secret is a Base64-encoded UTF-8 JSON object. The object, **before** Base64 encoding, has this structure:

```json
{
  "schema": "eya-signing-v1",
  "keystore_base64": "BASE64_OF_YOUR_EXISTING_JKS_BYTES",
  "keystore_sha256": "SHA256_HEX_OF_THE_SAME_JKS_BYTES",
  "store_password": "YOUR_EXISTING_STORE_PASSWORD",
  "key_alias": "YOUR_EXISTING_ALIAS",
  "key_password": "YOUR_EXISTING_KEY_PASSWORD"
}
```

Create this sensitive bundle **locally and privately** from an existing signing identity. GitHub's web editor cannot manufacture the owner's JKS. Never paste it into ChatGPT, Issues, pull requests, a README, or untrusted online encoders. Keep a secure backup.

Release uses R8 `isMinifyEnabled = true`, `isShrinkResources = true`, and owner certificate verification. The workflow refuses to publish a fake production APK if the secret is absent.

## Required physical acceptance

Install and launch the signed APK on a phone, test with GPS permission disabled and enabled, physically disconnect/reconnect the paired ELM327, check unavailable telemetry shows a dash, verify TTS and radio ducking, and test missing/corrupt audio resources as applicable. A successful CI build alone is not device acceptance.
