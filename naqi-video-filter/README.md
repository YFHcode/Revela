# Naqī (نقي) — Halal-Compliant Video Filtering Platform

> Working title. Final name TBD.

A web platform where a user uploads a video, the system automatically detects
visually intimate content (nudity, sexual content, kissing, intimacy,
suggestive/bed scenes), and the user then **streams the video with those scenes
seamlessly removed** or **downloads a filtered copy**. Strictness is a user
choice, applied as pure configuration over a single analysis pass.

**Core principle:** recall over precision — a missed intimate scene is a
product-killing failure; an over-flagged scene is a minor annoyance the user
can un-flag.

**Architecture in one sentence:** analyze once → store timestamped category
detections → generate any strictness level from the same analysis without
re-scanning → deliver via filtered HLS stream (watch) or re-encoded file
(download).

## Status

Draft specification for dev-team review — see [`docs/SPEC.md`](docs/SPEC.md)
for the full v1.0 product & engineering spec.

## Planned structure

No code yet. The spec proposes:

| Path (planned) | Purpose |
|---|---|
| `analyzer/` | Standalone, CLI-runnable detection pipeline (probe → shots → sampling → models → rules → `analysis.json`) |
| `api/` | FastAPI service (upload, analysis orchestration, playback, renders) |
| `web/` | React + Next.js frontend (upload, review timeline, player, library) |
| `workers/` | GPU analysis workers + CPU/FFmpeg render workers |
| `eval/` | Golden dataset harness, metrics, release gates |

## Owner

Youssef El Fhayel
