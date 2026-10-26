# Circuitries-apk

Android wrapper ([Capacitor](https://capacitorjs.com)) that packages the private
[Circuitries](https://github.com/LabisKouzoumpasis/Circuitries) web app into a signed APK.
The web app's files are copied into the APK, so the app works fully offline.

## How a build happens

The `Build APK` workflow (`.github/workflows/build-apk.yml`):

1. checks out `LabisKouzoumpasis/Circuitries` (branch `main` by default) using the `CIRCUITRIES_PAT` secret,
2. copies the web app into `www/` and runs `npx cap sync android`,
3. builds a release APK signed with the keystore from the secrets,
4. uploads it as a workflow artifact, and
   - on pushes to `main` / a dispatch from Circuitries: updates the **latest** release (`Circuitries-latest.apk`),
   - on tags `v*` (e.g. `v1.2.0`): creates a versioned release.

It runs on every push, on tags, manually (**Actions → Build APK → Run workflow**, where you can pick the
Circuitries branch to package) and when Circuitries sends a `circuitries-updated` dispatch.

Without `CIRCUITRIES_PAT` the run builds a placeholder APK (toolchain smoke test); without the keystore
secrets it builds a debug-signed APK.

## Repository secrets (Settings → Secrets and variables → Actions)

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the release keystore (`.jks`), base64-encoded on one line |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `circuitries` |
| `ANDROID_KEY_PASSWORD` | key password (same as the keystore password for PKCS12) |
| `CIRCUITRIES_PAT` | fine-grained personal access token, repository access **Circuitries** + **Circuitries-apk**, permission **Contents: Read and write** |

Optional repository variable `SOURCE_REF` changes the default Circuitries branch that gets packaged.

Keep a copy of the keystore and its password somewhere safe (password manager). If it is lost, installed
apps can't be updated with a new key — users would have to uninstall and reinstall.

## Local build

Requires Node 22, JDK 21 and the Android SDK.

```bash
npm ci
WEB_SRC=../Circuitries npm run sync      # copy web app + cap sync
cd android && ./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

App id: `com.labiskouzoumpasis.circuitries`.
