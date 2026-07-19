# Naqī (نقي) — Halal-Compliant Video Filtering Platform

> Working title. Final name TBD.

A web platform where a user uploads a video, the system automatically detects
visually intimate content (nudity, sexual content, kissing, intimacy,
suggestive/bed scenes), and the user then **streams the video with those scenes
seamlessly removed** or **downloads a filtered copy**. Strictness is a user
choice, applied as pure configuration over a single analysis pass.

- **Core principle:** recall over precision — a missed intimate scene is a
  product-killing failure; an over-flagged scene is a minor annoyance the user
  can un-flag.
- **Architecture in one sentence:** analyze once → store timestamped category
  detections → generate any strictness level from the same analysis without
  re-scanning → deliver via filtered HLS stream (watch) or re-encoded file
  (download).

Full product & engineering spec: [`docs/SPEC.md`](docs/SPEC.md).

---

## What's implemented

This repo contains a **runnable core**. The detection models (SPEC §7.4) and
the FFmpeg/HLS delivery paths are stubbed behind clean interfaces; everything
around them — the pipeline, rules engine, evaluation harness and API surface —
works today and is covered by tests.

| Path | Status | What it is |
|---|---|---|
| `analyzer/` | ✅ runnable | Standalone CLI pipeline: probe → shots → two-pass sampling → scorer → aggregation → rules → `analysis.json`. Ships a deterministic **mock** scorer so it runs with no GPU. |
| `rules.yaml` + `analyzer/rules.py` | ✅ runnable | The rules engine: levels, per-category sensitivity, hysteresis, C6-near-C1 context boost, post-processing. Levels are pure config over one analysis. |
| `eval/` | ✅ runnable | Leakage / recall / IoU metrics + a golden-set harness with the C1+C2 leakage release gate (SPEC §12). |
| `api/` | ✅ runnable | FastAPI v1 surface (SPEC §9) over an in-memory store standing in for PostgreSQL + Celery. |
| `analyzer/models/` real detectors | 🔧 stub | NSFW / NudeNet / SigLIP / X-CLIP slots registered lazily; wired in M1 after the license audit (SPEC App. C). |
| HLS packaging + FFmpeg render | 🔧 stub | Playback/render endpoints return signed-URL stubs; real packaging is M2 (SPEC §8). |
| `web/` | 📋 planned | Next.js SPA, built in M3 (see `web/README.md`). |

## Quick start

```bash
cd naqi-video-filter
pip install -e ".[api,dev]"        # core + API + test deps (no GPU needed)

# 1) Analyze a synthetic 6-minute film (no media/GPU required) at Level 2
python -m analyzer analyze --synthetic --duration 360 --level L2 -o analysis.json

# 2) Same analysis, different strictness — instant, no re-scan
python -m analyzer segments analysis.json --level L1
python -m analyzer segments analysis.json --level L3

# 3) Analyze a real file (needs ffmpeg on PATH; uses the mock scorer until M1)
python -m analyzer analyze movie.mp4 --level L2

# 4) Run the evaluation harness against the golden set (release gate)
python -m eval.harness tests/fixtures/golden_mini.json --level L3

# 5) Run the API
uvicorn api.main:app --reload    # http://localhost:8000/docs

# 6) Tests
pytest
```

`docker compose up` brings up the API + PostgreSQL + Redis + MinIO for local
work (GPU workers are commented out for CPU-only machines).

## How the pieces fit

```
upload ─▶ analyzer (once) ─▶ analysis.json ─┬─▶ rules engine ─▶ segments (any level)
                                            │                     │
                                            │            ┌────────┴────────┐
                                            │         watch: filtered HLS   download: FFmpeg
                                            │         (omit flagged chunks)  (trim+concat)
                                            ▼
                                        eval harness (leakage/recall gate)
```

The analyzer runs **once per video**; strictness levels and custom per-category
configs are resolved from the stored detections with no re-analysis. That's the
property everything else is built around.

## Layout

```
naqi-video-filter/
├── analyzer/          # detection pipeline (the heart — SPEC §7)
│   ├── probe.py       #   A: ffprobe metadata
│   ├── shots.py       #   B: shot boundaries (PySceneDetect / uniform fallback)
│   ├── sampling.py    #   C: two-pass frame sampling (1fps → 4fps borderline)
│   ├── aggregate.py   #   E1: top-3 shot scoring
│   ├── rules.py       #   E2: thresholds, hysteresis, context boost, post-proc
│   ├── pipeline.py    #   orchestration → analysis.json
│   ├── cli.py         #   `python -m analyzer …`
│   └── models/        #   scorer interface + mock + real-detector registry
├── api/               # FastAPI v1 (SPEC §9)
├── eval/              # metrics + golden-set harness (SPEC §12)
├── tests/             # pytest suite (rules, aggregation, metrics, API flow)
├── web/               # frontend (planned — SPEC §5, M3)
├── tools/annotator/   # local UI for labeling the golden set (SPEC §12)
├── golden/            # evaluation dataset: labels only, never media
├── rules.yaml         # filtering contract (SPEC App. A)
├── docker/            # container images
└── docs/SPEC.md       # full specification
```

## Owner

Youssef El Fhayel
