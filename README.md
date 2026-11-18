# Moshi + Tori migration patch

This patch keeps Moshi's Flutter launcher and existing screens as the app shell. It adds Tori's Android/Kotlin feature runtime and BitChord shared player modules, then exposes a `pokke/tori` method channel so Moshi can open the native Tori workspace. The native Tori workspace contains Tori's player, settings, profile/social, manga, servers, YouTube, Canvas, lyrics, external-account and provider integrations as present in the supplied Tori source. Canvas media is copied under the Android assets tree.

## Files safe to delete from Moshi

**None identified.** Existing Moshi files remain referenced by the Flutter app or were not proven to be obsolete by a complete reference/build analysis. In particular, do not delete Moshi's current player or settings implementation yet.

## Entry point

- `lib/services/tori_native_bridge.dart` — Flutter bridge; call `ToriNativeBridge.open()` from the Moshi player/settings entry point where the native workspace should open.
- `android/app/src/main/kotlin/com/example/pokke/PokkeChannels.kt` — registers the native bridge.
- `android/app/src/main/kotlin/com/pokke/music/ToriFeatureActivity.kt` — Tori's native Compose entry activity.

## Important integration boundary

The supplied Tori project is a native Android/Jetpack Compose application, while Moshi is Flutter. Tori does not contain Flutter/Dart screens to merge directly into Moshi's widget tree. This patch therefore hosts Tori's feature UI in a native Activity launched from Moshi rather than rewriting every Compose screen as Flutter. Moshi's existing Flutter UI remains intact. The two UI trees are not a single unified Flutter navigation stack.

Tori's Supabase and provider configuration files are copied with its Kotlin sources; credentials that were not present in the supplied source remain unset. No secrets or API keys have been fabricated.

## Build/toolchain note

Tori's BitChord modules require Kotlin 2.4.20, Compose Multiplatform 1.10.3, AGP 9.1.1 and Gradle 9.5. The Android Gradle configuration is updated to those versions. Confirm the installed Flutter SDK and Android Gradle Plugin compatibility in CI before release.
