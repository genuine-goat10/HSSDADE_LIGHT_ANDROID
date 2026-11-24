# HSSDADE-LIGHT.CORP — Android source 0.4.0 (cloud APK build)

This project is an early Android prototype for residential electrical quotations.

## Included
- Kotlin + Jetpack Compose application.
- Local Room database for clients and quotations.
- Residential electrical service catalog (36 services).
- Multi-service quotation entry and bilingual Spanish/English quotation content.
- Customer-facing PDF export.
- GitHub Actions workflow to compile a debug APK in the cloud from an Android phone.

## Build an APK using only an Android phone
1. Create a GitHub account or sign in at https://github.com.
2. Create a **private** repository named `HSSDADE-LIGHT-CORP`.
3. Upload the contents of this project folder to the repository root (not the ZIP file itself).
4. Open the repository's **Actions** tab and enable Actions if GitHub asks.
5. Select **Build Android APK** and tap **Run workflow**.
6. When the run finishes successfully, open that run and download the artifact named `HSSDADE-LIGHT-CORP-debug-APK`.
7. Extract the downloaded artifact ZIP and install `app-debug.apk` on the Android phone. Android may ask permission to install unknown apps for the browser or file manager used.

The workflow uses GitHub-hosted build machines, Java 17, Android SDK 35, and Gradle 8.9. GitHub Actions usage limits and billing depend on the repository visibility and account plan.

## Important prototype limitations
- This is a **debug/test APK**, not a signed production release for public distribution.
- The project has not yet been compiled or tested on a physical device. The first cloud build will reveal any build errors that need fixing.
- Google sign-in and automatic cloud synchronization are not implemented.
- Local/offline storage is present; syncing after reconnecting is not implemented.
- Selected services are currently stored together in the quotation record, not as independent database line items.
- The minimum-visit charge is stored as a setting but is not automatically applied.
- Review labor rates and other estimating settings in the app before using quotes with customers.


## Build cloud (v0.4.1)
- Compose compiler plugin aligned with Kotlin 2.0.21.
- GitHub Actions installs Gradle 8.9 and Android SDK 35, so the Gradle Wrapper is not required by the current workflow.
- The current manifest uses the built-in Android theme and does not reference app resources; a res/ directory is not required for this configuration.
- Build workflow: `.github/workflows/android-apk.yml` (manual dispatch or push to main).
