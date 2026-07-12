# Revela — Implementation Plan

*Personal behavioral pattern engine for Android. Single-user, on-device, local-first.*
*(Working title in the brief: MIRROR. Repo/app name: **Revela**.)*

This plan turns the engineering build brief into an actionable development plan:
resolved decisions, architecture, module layout, data model, and a
milestone-by-milestone work breakdown with acceptance criteria.

---

## 1. What we are building (one paragraph)

An Android app that passively observes the owner's own device usage (app usage,
screen/unlock events; later notification timing, location, calendar), rolls the
raw event stream up into summaries, runs a statistical pattern-mining engine
over those summaries (periodicity, sequences, change-points, deviations,
cross-stream lagged correlation), and surfaces what it finds as a feed of
neutral, plain-language insights. All raw data stays on the device, encrypted.
The LLM (OpenAI API for now) narrates and answers questions over **abstracted
aggregates only**; it never does pattern discovery and never sees raw events
or content (brief rule D4).

Non-goals: productivity scoring, streaks/goals/guilt mechanics, cloud sync,
accounts, telemetry, ads, iOS.

---

## 2. Resolved decisions (brief §15)

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| Q1 | Graph storage | **Graph-shaped tables in the existing SQLite** (`nodes`, `edges`) | No new dependency; Room queries + in-memory Louvain over a few thousand edges is trivial at single-user scale. Revisit only if edge counts explode. |
| Q2 | LLM choice | **OpenAI API** (e.g. `gpt-4o-mini` for narration, a stronger model for the query interface), user-supplied API key stored in encrypted prefs | Fast to ship, no device-RAM constraints, good narration quality. Narration (L1) keeps a **template fallback** (M3) so the app is fully functional offline / without a key. An on-device model (MediaPipe + Gemma) remains a possible later swap behind the same `Narrator`/`QueryEngine` interfaces. |
| Q3 | Cloud LLM permitted? | **Yes — with D4 strictly enforced.** The OpenAI API may receive **abstracted patterns and summary aggregates only** — never raw events, notification titles/senders, message content, or raw location points. Enforced structurally: the LLM layer can only read a curated "LLM-safe view" of the data (see §3.3), not the DB. | User's call for velocity. D4's aggregation firewall keeps the privacy posture: what leaves the device is the same material that appears on an insight card. |
| Q4 | Silent-observation window | **Default 21 days, user-adjustable 7–28 in onboarding** | 3 weeks covers 3 full weekly cycles — the minimum for weekday/weekend baselines and 7-day periodicity to be non-noise. Dashboard unlocks at day 8 (raw summaries only); insights feed unlocks at day 21. |
| Q5 | UsageStats poll interval | **WorkManager periodic, 15 min** (WorkManager's minimum), cursor-based | Battery-negligible since `queryEvents` reads a system buffer. The cursor + append-only log makes interval choice non-critical; if the OS defers the job, nothing is lost. |
| Q6 | Min API level | **minSdk 26 (Android 8.0), targetSdk 35** | 26 gives `UsageEvents` screen-interactive/keyguard constants (`SCREEN_INTERACTIVE` etc. are API 28 — see mitigation in §9) and sane background limits to design against from day 1. Practically: **minSdk 28** if we want the screen/keyguard event constants without fallback code; decide at M1 kickoff — plan assumes **28**. |
| Q7 | Reflex-check threshold | **< 15 s confirmed**, stored as a constant in one place, re-derivable | Threshold lives only in rollup code; because raw log is source of truth, changing it later just means re-running rollups. |

---

## 3. Architecture

Four-stage pipeline, exactly as the brief's §7, mapped onto Android components:

```
┌────────────┐   ┌──────────────┐   ┌───────────────┐   ┌─────────────┐
│  CAPTURE    │→ │  RAW EVENT   │→ │  ROLLUP +      │→ │  PRESENT     │
│  collectors │   │  LOG (Room+  │   │  ANALYSIS      │   │  Compose UI  │
│  (WorkMgr,  │   │  SQLCipher,  │   │  (WorkManager, │   │  dashboard + │
│  listeners) │   │  append-only)│   │  charging+idle)│   │  insights    │
└────────────┘   └──────────────┘   └───────────────┘   └─────────────┘
```

Key invariants (enforced in code review, not just docs):

1. **Raw log is append-only.** Nothing updates or deletes raw rows except the
   user-facing delete/wipe controls (D6).
2. **Everything downstream is re-buildable.** Every rollup and analysis job is
   idempotent, keyed by date/window, and can be replayed over history.
3. **Watermarks per collector and per rollup.** Late/out-of-order OS batches
   never double-count or leave gaps.
4. **No heavy compute on the UI thread.** UI reads summary + insight tables only.
5. **Stats discover, LLM narrates.** No LLM call anywhere in the `analysis`
   module.
6. **The LLM sees aggregates only (D4).** All LLM traffic goes through the
   `LlmSafePayload` layer (§3.3); no module hands raw tables to the network.

### 3.1 Module layout (Gradle modules)

Single-activity Compose app, multi-module to keep the invariants mechanical:

```
:app                 — DI wiring, navigation, onboarding, settings
:core:db             — Room + SQLCipher, entities, DAOs, keystore key mgmt
:core:model          — plain data types shared across modules
:capture             — collectors: UsageStatsCollector (P1),
                       NotificationCollector, LocationCollector,
                       CalendarCollector (P2); cursor persistence
:pipeline            — WorkManager jobs: rollup workers, analysis scheduler
:analysis            — the pattern-mining engine (pure Kotlin/JVM, no Android
                       deps → unit-testable on the JVM)
:insights            — insight generation: maps analysis results → insight
                       rows; templated narration; (M5) LLM narration + query
:ui                  — dashboard, insights feed, heatmaps, detail views
```

`:analysis` being a pure JVM module is the single most important structural
choice: every algorithm in §8 of the brief gets fast JVM unit tests with
synthetic fixtures (see §8 Testing below).

### 3.2 Stack

- Kotlin, Jetpack Compose, Material 3
- Room + SQLCipher (`net.zetetic:sqlcipher-android`); passphrase generated once,
  stored in Android Keystore (StrongBox where available); optional biometric
  gate via `BiometricPrompt`
- WorkManager for all background work; heavy jobs constrained
  `setRequiresCharging(true)` + `setRequiresDeviceIdle(true)`
- Stats: hand-rolled where simple (autocorrelation, z-scores, CUSUM,
  cross-correlation) + **Apache Commons Math** for FFT; **SMILE** only if/when
  DBSCAN (Phase 2) — avoid pulling it in for Phase 1
- OpenAI API (M5) via plain OkHttp/Retrofit + kotlinx-serialization (no heavy
  SDK dependency); user-supplied API key in `EncryptedSharedPreferences`
- Network access is used **only** by the `:insights` LLM layer, and only with
  `LlmSafePayload` content; capture/pipeline/analysis modules have no network
  code, and a network-security config restricts traffic to `api.openai.com`

### 3.3 The LLM privacy firewall (`LlmSafePayload`)

Because a cloud LLM is now in play, D4 is enforced in code, not convention:

- A single `LlmGateway` class owns the API key and the HTTP client. It accepts
  only `LlmSafePayload` values — typed structures built exclusively from
  rollup aggregates and insight `stat_payload`s (daily totals, histograms,
  detected periods/lags, app display names).
- Forbidden at the type level: raw `events` rows, notification titles/senders,
  contact names (contacts are pseudonymized to stable labels like
  "Contact #3" before leaving the device; the mapping stays local and the UI
  re-substitutes real names when rendering answers), raw location coordinates
  (places leave only as their user-assigned labels or "Place #2").
- The query interface's SQL tool (M5) runs **locally**: the model composes a
  query plan, the app executes read-only SQL on-device against whitelisted
  rollup tables, and only the aggregated result rows (post-pseudonymization)
  are sent back to the model.
- Everything sent to the API is logged to a local, user-viewable "what left
  the device" audit screen in Settings.

---

## 4. Data model (Room schema, v1)

Concrete rendering of brief §6. All tables in one encrypted DB.

```sql
-- 6.1 raw log (append-only)
events(id PK AUTOINCREMENT, ts INTEGER NOT NULL, type TEXT NOT NULL,
       app_pkg TEXT, entity_id INTEGER REFERENCES entities(id),
       payload TEXT /*JSON*/, source TEXT NOT NULL)
  INDEX (ts), INDEX (type, ts)

-- collector cursors / rollup watermarks
watermarks(key TEXT PK, value INTEGER NOT NULL)   -- e.g. "usagestats.cursor",
                                                  -- "rollup.daily.2026-07-11"

-- 6.2 rollups (all keyed by local date; re-runnable)
sessions(id PK, app_pkg, start_ts, end_ts, duration_s, pickup_type TEXT
         /* NORMAL | REFLEX */)
usage_hourly(date TEXT, hour INT, app_pkg TEXT, total_seconds INT,
             open_count INT, PRIMARY KEY(date, hour, app_pkg))
usage_daily(date, app_pkg, total_seconds, open_count, first_use, last_use,
            PRIMARY KEY(date, app_pkg))
day_summary(date PK, first_unlock, last_use, total_screen_time_s,
            pickup_count, reflex_check_count, day_type TEXT)
comms_daily(date, contact_id, app_pkg, msg_notif_count,
            by_hour_histogram TEXT /*JSON int[24]*/,
            PRIMARY KEY(date, contact_id, app_pkg))          -- Phase 2
place_daily(date, place_id, arrival_ts, depart_ts, dwell_seconds)  -- Phase 2

-- 6.3 entities
entities(id PK, kind TEXT /*APP|CONTACT|PLACE|TOPIC*/, display_name,
         aliases TEXT /*JSON*/, metadata TEXT /*JSON*/)

-- 6.4 insights
insights(id PK, created_ts, type TEXT, confidence REAL,
         entity_ids TEXT /*JSON*/, window_start, window_end,
         stat_payload TEXT /*JSON*/, text TEXT,
         dismissed INTEGER DEFAULT 0, pinned INTEGER DEFAULT 0)
  -- dedupe key: (type, entity_ids, window) — an insight is UPSERTed, so the
  -- feed doesn't repeat "app X has a 7-day cycle" every week

-- 6.5 graph (Phase 3)
nodes(id PK, entity_id FK, ...)   edges(src, dst, type, weight, first_ts,
                                        last_ts, PRIMARY KEY(src,dst,type))
```

Timezone rule: all raw `ts` are epoch millis UTC; all rollup keys are **local
dates computed with the device's timezone at rollup time**, and `day_summary`
stores the zone id used, so travel doesn't silently shear the baselines.

---

## 5. Milestone plan

Ordered exactly as brief §14, with concrete scope and acceptance criteria.
Milestones M1–M5 are Phase 1 + LLM; M6 is Phase 2; M7–M8 are Phase 3/optional.

### M1 — Skeleton + storage + first permission
Scope:
- Gradle multi-module project as in §3.1; CI (GitHub Actions: build + JVM tests + lint)
- `:core:db` — Room + SQLCipher, keystore-backed passphrase, schema v1
  (events, watermarks, entities, insights tables; rollup tables can land in M2)
- Onboarding v0: explain the app → deep-link to Usage Access settings
  (`Settings.ACTION_USAGE_ACCESS_SETTINGS`) → verify grant via
  `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, ...)` on resume
- Battery-optimization exemption step (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`)
- `UsageStatsCollector` v0 behind a WorkManager periodic job (15 min):
  `queryEvents(cursor, now)` → map to raw `events` rows → advance cursor
  atomically with the insert (single transaction)
- Debug-only "raw log viewer" screen to prove events land

Accept when: fresh install → onboarding grants usage access → within 30 min
the raw log shows ACTIVITY_RESUMED/PAUSED, SCREEN_*, KEYGUARD_* events;
killing/restarting the app produces no gaps or duplicate rows (cursor test).

### M2 — Rollups + Dashboard v0
Scope:
- Session reconstruction from the raw log: pair RESUMED/PAUSED per package,
  close sessions on SCREEN_OFF/SHUTDOWN, tag `REFLEX` for < 15 s
  pickup-sessions that end with screen-off (no second app)
- Nightly rollup worker (charging+idle): fold events since watermark into
  `sessions`, `usage_hourly`, `usage_daily`, `day_summary` — idempotent per
  date (delete-and-recompute the affected dates, never increment blindly)
- First-unlock / last-use derivation with a 4 a.m. "day boundary" rule
  (a 1 a.m. doomscroll belongs to the previous day)
- Dashboard v0 (Compose): today + last-7-days screen time, per-app bars,
  hour×weekday heatmap (per app and overall), pickups & reflex-check counts
- Settings v0: pause capture toggle, full wipe (confirmed, re-keys DB)

Accept when: rollups replayed twice over the same window produce identical
tables; heatmap matches a hand-computed fixture; a simulated late OS batch
(events inserted for yesterday after rollup ran) is picked up by the next run.

### M3 — Analysis v1 + Insights feed v0 + Silent window
Scope (all in `:analysis`, pure JVM, each with synthetic-fixture tests):
- **Profiles** (8.1): normal-range model per (behavior, hour, day_type) —
  rolling median + IQR
- **Periodicity** (8.2): autocorrelation + Commons-Math FFT periodogram over
  daily series; report period + strength; require ≥ 3 observed cycles before
  surfacing
- **Deviation** (8.8): z-score/IQR departure of today vs profile → "today is
  unusual" insight candidates
- **Change-point** (8.6): PELT or CUSUM over daily series → "habit
  started/stopped/shifted"
- Insight generation in `:insights`: analysis result → `insights` row with
  `stat_payload` + **templated** natural-language text (string templates per
  insight type, neutral P1 phrasing; copy reviewed against "no judgment" rule)
- Insights feed UI: cards, tap → supporting stat detail, dismiss/pin
- **Silent-observation window** (P5/Q4): countdown state machine — days 0–7
  nothing but onboarding copy ("watching quietly"), day 8 dashboard unlocks,
  day 21 insights unlock; length configurable 7–28 days at onboarding
- Insight dedup/UPSERT policy (§4) + confidence floor so the feed starts
  quiet rather than noisy

Accept when: synthetic 60-day fixture with a planted 7-day cycle, one change
point, and two anomalous days yields exactly those insights (no false cycle on
a shuffled control series); feed shows nothing before the window elapses.

### M4 — Cross-stream lagged correlation + sequence mining  ★ the payoff milestone
Scope:
- **Lagged cross-correlation** (8.9): engine that takes pairs of daily series
  (evening screen time, first-unlock time, per-app minutes, pickup count, …)
  and scans lags −3…+3 days plus within-day lead/lag (notification→session
  starts). Pearson on rank-transformed series; Benjamini–Hochberg correction
  across the pair×lag family — this engine tests many hypotheses and MUST
  control false discoveries or the feed becomes horoscope
- Candidate-pair registry (explicit list, not all-pairs) to keep the search
  space and the multiple-testing burden sane; start with ~15 curated pairs
  from brief §8.9 examples
- **Sequence mining** (8.4): PrefixSpan over per-session app-open sequences,
  bucketed by daypart; min-support tuned so "morning routine" emerges from
  fixtures; surfaces top ordered patterns not already implied by shorter ones
- New insight types wired end-to-end: feedback loops ("high evening screen
  time → later first unlock next morning, r=…, n=… days"), routines
  ("most mornings: unlock → A → B → C")
- Association rules (8.5, FP-growth on daily baskets) **only if time allows**;
  it's lower-leverage than 8.9 and can slip to M6

Accept when: planted lagged dependency (evening minutes ⇒ next-morning unlock
+40 min, r≈0.6) is found at the right lag and direction, and a permuted control
yields zero discoveries at the chosen FDR; planted morning sequence is mined.

### M5 — LLM layer (OpenAI API): narration + query
Scope:
- `Narrator` interface with two impls: `TemplateNarrator` (M3, always
  available) and `OpenAiNarrator` (`gpt-4o-mini` class model)
- Setup flow: user pastes their OpenAI API key (stored in
  `EncryptedSharedPreferences`), a clear explanation of exactly what data can
  leave the device (D4 / §3.3), and an off switch that reverts everything to
  templates
- `LlmGateway` + `LlmSafePayload` firewall (§3.3), including contact/place
  pseudonymization and the "what left the device" audit log
- L1 narration: prompt = insight type + `stat_payload` + P1 style rules;
  output validated (length, no numbers invented — reject and fall back to
  template if the payload numbers don't appear)
- L3 query interface v1: chat screen; the model gets a system prompt
  describing the summary tables and a tool — `run_readonly_sql` (SELECT-only,
  whitelisted tables: rollups + insights, never `events`) — executed
  **locally**, with result rows pseudonymized before being returned to the
  API (§3.3)
- Graceful degradation: no key / no network / API error → templates and a
  quiet notice, never a broken feed

Accept when: with a key configured, "when do I usually wake up on weekends?"
is answered correctly from `day_summary`; the audit screen shows only
aggregate payloads; with the key removed or network off, narration falls back
to templates cleanly.

### M6 — Phase 2: comms timing, places, calendar
Scope:
- `NotificationCollector` (NotificationListenerService): package, postTime,
  sender/title → contact entity resolution; **no body text persisted** (D7);
  debounce updates of the same notification key
- Contact entity resolution v1: normalize titles/senders into CONTACT entities
  (exact/alias matching; LLM-assisted alias merge waits for Phase 3)
- `LocationCollector`: FusedLocationProvider passive/batched low-power
  updates; DBSCAN (now bring in SMILE, or hand-rolled) → `PLACE` entities with
  user labeling UI (home/work/gym/…); `place_daily` dwell rollup
- `CalendarCollector`: recurring-event titles/times as signal rows
- New insight types: communication timing per contact, relationship drift
  (per-contact frequency trend over months — reuses change-point engine),
  place-based weekly rhythms (reuses periodicity engine on place dwell series)
- Onboarding additions for each grant, each optional and individually
  revocable; per-entity delete (contact/place/app/time-range) lands here
  because entities now exist (D6)

Accept when: each Phase 2 permission is individually deniable with the app
degrading gracefully; deleting a contact removes its events, rollups, and
insights; drift insight fires on a fixture where contact frequency halves.

### M7 — Phase 3: knowledge graph + modes
Scope:
- `nodes`/`edges` tables; edge builders from co-occurrence windows
  (CO_OCCURRED, LED_TO from session succession, CO_LOCATED, MESSAGED, VIEWED)
- Louvain/Leiden community detection (in-memory over the SQLite edges) →
  candidate "modes"; naming via the LLM layer over the community's member
  entities + time signature ("Tuesday evenings: gym + podcast + no messaging"),
  through the same §3.3 firewall (pseudonymized contacts/places). Note: any
  future raw-CONTENT processing (M8) still requires an on-device model per D5.
- Mode insights + a graph-ish exploration screen (simple: mode cards, not a
  force-directed toy)

### M8 — (Optional, separate mini-project) on-device content capture
Per brief §4.4/4.3: AccessibilityService/MediaProjection + OCR, on-device
only, explicit opt-in. **Not planned here** — requires its own design doc,
threat model, and the Android 13+ restricted-settings UX. Nothing in M1–M7
depends on it.

---

## 6. Cross-cutting workstreams

**Privacy enforcement (D1–D7)** — not a milestone, a standing rule set:
- CI check: `INTERNET` permission absent from the merged manifest until M5,
  then present only for the `:insights` LLM layer (network security config
  restricts traffic to `api.openai.com`; no other module contains network code)
- D4 firewall: all outbound payloads built via `LlmSafePayload` (§3.3);
  contact/place identities pseudonymized before leaving the device; local
  audit log of everything sent
- No analytics/crash SDKs; crash logs stay in a local ring buffer viewable in
  settings
- Destructive actions (range delete, entity delete, wipe) always confirmed,
  implemented as raw-log deletes + full re-rollup of affected dates

**Testing strategy:**
- `:analysis` — JVM unit tests with synthetic generators (planted periods,
  change-points, lags) + shuffled/permuted negative controls for every
  detector. False-positive discipline is a test, not a hope.
- `:pipeline` — Robolectric tests for idempotent rollups, watermark/late-data
  scenarios, day-boundary and timezone-change cases
- `:capture` — instrumented tests with fake `UsageEvents` streams; a
  `DebugSeeder` that injects N days of realistic synthetic raw events so UI
  and analysis are developable without waiting 3 real weeks
- Manual test matrix: one stock Android, one Samsung, one Xiaomi (aggressive
  battery managers, per brief §2)

**The 3-week problem:** the silent window means real-data feedback is slow.
The `DebugSeeder` (M2) + a debug flag to shrink the window to minutes is how
we develop; dogfooding on the team's own devices starts at M2 and runs
continuously so M3/M4 detectors meet real data the day they're written.

---

## 7. Insight catalog → engine → milestone map

| Insight (brief §9) | Engine | Ships in |
|---|---|---|
| Chronotype & social jetlag | first-unlock/last-use + weekday/weekend profiles | M3 |
| Reflex-check reality | session segmentation (8.3) | M2 (number) / M3 (insight) |
| "Today is unusual" | deviation (8.8) | M3 |
| Weekly/monthly rhythms | periodicity (8.2) | M3 |
| Habit started/stopped | change-point (8.6) | M3 |
| Morning/evening routines | PrefixSpan (8.4) | M4 |
| Cross-stream feedback loops | lagged correlation (8.9) | M4 |
| Comms timing per contact | comms rollups | M6 |
| Relationship drift | trend + change-point on comms | M6 |
| Contexts/modes | graph communities (8.10) | M7 |

---

## 8. Risks & mitigations

| Risk | Mitigation |
|---|---|
| OEM battery managers silently kill collection → data gaps | Onboarding exemption step; gap detection (no events for > N hours while device clearly used → "collection gap" marker so analysis excludes, not misreads, gaps); dogfood on Xiaomi/Samsung early |
| API-level differences in `UsageEvents` constants (Q6) | minSdk 28 assumption; verify constants on the 3-device matrix in M1, before anything is built on top |
| Insight feed feels like horoscope (false positives) | FDR correction (M4), ≥3-cycle rule for periodicity, confidence floor, negative-control tests as CI gates |
| Insight feed feels judgmental (violates P1) | Copy review checklist per template; no comparative/normative words ("too much", "wasted", "should") allowed in templates or LLM style prompt |
| LLM narrates badly or hallucinates numbers | LLM output rejected unless payload numbers appear verbatim; template fallback is always available |
| Cloud LLM leaks more than intended (D4 violation) | `LlmSafePayload` type firewall (§3.3); pseudonymized contacts/places; single `LlmGateway` chokepoint; user-viewable audit log of every outbound payload; network config pinned to `api.openai.com` |
| No network / no API key | App is fully functional on templates; LLM features are additive, never load-bearing |
| SQLCipher + Room version friction | Pin versions in M1; DB layer isolated in `:core:db` so a swap (e.g. to `sqlcipher-android` successor) touches one module |
| Timezone/DST shears daily baselines | Local-date rollup keys + stored zone id + 4 a.m. day boundary; explicit tests |
| Scope creep toward content capture | M8 is fenced off behind a separate design doc; nothing may depend on it |

---

## 9. Immediate next steps (M1 kickoff)

1. Confirm minSdk 28 on the target device matrix (Q6 residual).
2. Scaffold the Gradle multi-module project + CI.
3. Land `:core:db` (schema v1 + keystore/SQLCipher) with tests.
4. Build onboarding grant flow + `UsageStatsCollector` + raw-log debug viewer.
5. Start team dogfooding the collector immediately — every day of real raw
   log collected now is a day of baseline the analysis milestones can use.
