# Live-now — Android APK build

This repository contains the Android project for YouTube Loop Live.

## Build APK automatically on GitHub

1. Push the project to the `main` or `master` branch of `divyanshkr716-bot/Live-now`.
2. Open the repository's **Actions** tab and select **Build Android APK**.
3. Choose **Run workflow** if it has not run automatically.
4. Open the completed run and download the `Live-now-debug-apk` artifact. Extract it to get the debug APK.

The workflow builds a debug APK for testing. It is not a Play Store release-signed APK.

## Build in Termux

Building Android apps directly in Termux requires a compatible Java, Gradle, and Android SDK setup. The GitHub Actions workflow above is the recommended build path for this project because it provisions the Android SDK automatically.

## Notes

- `google-services.json` is not required for the Gradle configuration to proceed; Google Services is configured to warn if the file is missing. Firebase-backed features may still need a valid Firebase configuration at runtime.
- Never commit API keys, signing keys, or passwords to the repository.
