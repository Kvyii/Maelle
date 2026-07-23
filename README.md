# Maelle

A clean-room Android novel reader with an integrated AI reading assistant. Maelle
is the ground-up successor to an earlier QuickNovel fork — same functionality,
none of the tech debt.

## What it does

- **Library** — the series you're following, at a glance.
- **Search** — pick a provider, search within it, tap a result to add it and open it.
- **Series detail** — chapters listed newest → oldest. Tap to read, long-press a
  chapter to *mark it and everything below (older) as read*.
- **Offline downloads** — chapters are cached to local storage for offline reading.
- **Reader** — clean text reader with an OpenRouter-powered assistant: ask it to
  explain a selected word or phrase using the surrounding paragraph as context.
- **Settings** — configure the assistant (OpenRouter API key, model, prompt).

## Architecture

Two Gradle modules:

- **`:core`** — pure JVM (no Android). All content providers plus the extraction
  test harness live here. Providers reach the network only through an injected
  `HttpClient`, so every provider is unit-testable off-device.
- **`:app`** — Jetpack Compose UI, Room persistence, DataStore settings,
  WorkManager-friendly downloads.

Package root: `com.kvyii.maelle`.

## Providers

~27 providers ported from the original active list, e.g. AllNovel, NovelBin,
NovelFull, FreeWebNovel, LibRead, Royal Road, Scribblehub, WTR-LAB, Anna's Archive,
WuxiaBox, MtlNovel, and more. See
[`ProviderRegistry`](core/src/main/kotlin/com/kvyii/maelle/core/ProviderRegistry.kt).

## Testing the providers

Every provider must satisfy one extraction contract: *search returns results →
first result loads → content is extractable* (chapter text, or download links for
epub sources). Two suites enforce it:

- **Fixture tests** (default, offline, CI-safe) replay recorded HTML. Providers
  without recorded fixtures are skipped.
- **Live tests** (opt-in, tagged `live`) hit the real sites — the canary for when
  a site changes its markup.

```bash
# Set JAVA_HOME to a JDK 17+ (e.g. Android Studio's bundled JBR) first.

# Offline fixture tests
./gradlew :core:test

# Live extraction against every provider
./gradlew :core:test -Dmaelle.live=true

# One provider, and record fixtures while doing it
./gradlew :core:test -Dmaelle.live=true -Dmaelle.provider=NovelBin -Dmaelle.record=true
```

Recorded fixtures land in `core/src/test/resources/fixtures/`; commit them to make
the offline suite meaningful.

## Building

Requires the Android SDK and a JDK 17+. Set `JAVA_HOME`, then:

```bash
./gradlew :app:assembleDebug
```

## Legal

Maelle hosts no content. Like any search engine, it crawls and links to publicly
accessible third-party sites. Any copyright concerns should be directed to those
sites. For personal and educational use only; use at your own risk.
