# Revela

A personal behavioral pattern engine for Android. Single-user, on-device,
local-first. Revela quietly observes how you use your phone and surfaces the
patterns you can't see from the inside — rhythms, routines, triggers, and
drift over time. A mirror, not a scold.

See [PLAN.md](PLAN.md) for the full implementation plan and
milestone breakdown.

## Status

**Milestone M4** — cross-stream lagged correlation + sequence mining:

- Lagged cross-correlation engine (§8.9, the highest-leverage detector):
  Spearman rank correlation over a curated (driver → outcome, lag) registry
  — evening/late-night screen time vs next-morning first pickup, day-start
  time vs total screen time, etc. — with Benjamini–Hochberg FDR correction
  across the whole hypothesis family so discoveries mean something
- Routine mining (§8.4): PrefixSpan over app-open bursts; surfaces the
  recurring morning-opening and evening-wind-down sequences
- Both ship as new insight types ("Connected patterns", "Routine") with the
  same planted-fixture + verified-silent-control test discipline

**Milestone M3** — analysis engine + insights feed v0:

- Pattern detectors (pure JVM, statistics only — the LLM never does
  discovery): periodicity via autocorrelation, change-point via SSE split
  with an effect-size gate, robust deviation (median + IQR), chronotype
  (weekday/weekend first-unlock shift), reflex-check reality
- Every detector is tested against planted fixtures AND verified-silent
  shuffled controls, so the feed can't drift into horoscope territory
- Insight engine drafts neutral, curious text (P1) with the numbers kept in
  a stat payload; UPSERT dedupe means the feed never repeats itself and
  dismissed insights stay dismissed
- Insights feed UI: plain-language cards, supporting numbers on tap,
  pin/dismiss; unlocks after the silent-observation window (day 21 default)

**Milestone M2** — rollups + dashboard v0:

- Session reconstruction from the raw log (foreground/background pairing,
  screen-off closing, 6h data-gap clamp), pickup detection, and sub-15s
  "reflex check" tagging — all in a pure-JVM processor with unit tests
- Idempotent rollup pass (WorkManager, charging-constrained periodic +
  on-demand) into `sessions`, `usage_hourly`, `usage_daily`, `day_summary`,
  with the 4 a.m. behavioral-day boundary rule
- Dashboard v0: stat tiles (screen time, pickups, reflex checks, first
  unlock / last use), 7-day screen-time bars, hour×weekday usage heatmap,
  today's top apps
- Settings v0: pause observation, full wipe (with confirmation), developer
  mode (bypasses the quiet window for dogfooding)

**Milestone M1** — skeleton + storage + capture:

- Multi-module Gradle project (Kotlin, Jetpack Compose)
- Encrypted storage: Room + SQLCipher, passphrase wrapped by an Android
  Keystore key
- Append-only raw event log with per-collector cursors (gap/duplicate-free
  across restarts)
- UsageStats collector on a 15-minute WorkManager schedule (app foreground/
  background, screen on/off, unlock events)
- Onboarding that walks the usage-access and battery-exemption grants and
  verifies them
- Silent-observation window (default 21 days) before anything is shown
- Debug raw-log viewer

## Privacy posture

- All raw data stays on this device, encrypted at rest.
- The app declares **no INTERNET permission** (CI enforces this). The only
  planned network use is the M5 LLM layer, which may send **abstracted
  aggregates only** — never raw events or content.
- No analytics, no telemetry, no accounts, no ads.

## Building

```
./gradlew build          # assembles + runs JVM unit tests + lint
./gradlew :app:assembleDebug
```

Requires JDK 17+ and the Android SDK (compileSdk 35). minSdk is 28
(Android 9).
