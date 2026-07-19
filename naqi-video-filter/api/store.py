"""In-memory store — a stand-in for PostgreSQL + Redis/Celery (SPEC §6, §10).

v1's real deployment persists to PostgreSQL and runs analysis/render on Celery
workers. This module keeps the API demonstrable and testable without those
services: it holds videos, analyses and renders in process, and runs "analysis"
synchronously via the analyzer's synthetic mode. Swap this for real repositories
+ a job queue in M2 — the router surface stays the same.
"""
from __future__ import annotations

import threading
import uuid
from dataclasses import dataclass, field

from analyzer import AnalysisResult, Rules, analyze_synthetic
from analyzer.config import FilterConfig


def _id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex[:12]}"


@dataclass
class VideoRecord:
    id: str
    title: str
    size_bytes: int
    status: str = "uploading"
    progress_pct: int = 0
    stage: str | None = None
    duration_s: float | None = None
    analysis: AnalysisResult | None = None
    overrides: list[dict] = field(default_factory=list)
    config: dict = field(default_factory=lambda: {"preset": "L2", "categories": {}})
    pipeline_version: str | None = None


@dataclass
class RenderRecord:
    id: str
    video_id: str
    state: str = "queued"
    config: dict = field(default_factory=dict)
    download_url: str | None = None
    size_bytes: int | None = None


class Store:
    """Process-local store. Thread-safe for the dev server."""

    def __init__(self) -> None:
        self._videos: dict[str, VideoRecord] = {}
        self._renders: dict[str, RenderRecord] = {}
        self._lock = threading.Lock()
        self._rules = Rules.load()

    @property
    def rules(self) -> Rules:
        return self._rules

    # --- videos ---------------------------------------------------------
    def create_video(self, title: str, size_bytes: int) -> VideoRecord:
        rec = VideoRecord(id=_id("vid"), title=title, size_bytes=size_bytes)
        with self._lock:
            self._videos[rec.id] = rec
        return rec

    def get_video(self, video_id: str) -> VideoRecord | None:
        return self._videos.get(video_id)

    def list_videos(self) -> list[VideoRecord]:
        return list(self._videos.values())

    def delete_video(self, video_id: str) -> bool:
        with self._lock:
            existed = self._videos.pop(video_id, None) is not None
            for rid in [r.id for r in self._renders.values() if r.video_id == video_id]:
                self._renders.pop(rid, None)
        return existed

    def run_analysis(self, video_id: str, duration_hint_s: float | None = None) -> VideoRecord:
        """Synchronous stand-in for the async analysis job (SPEC FR-2).

        Real pipeline: virus scan -> mezzanine transcode -> detection. Here we
        run the analyzer in synthetic mode so the whole API is exercisable.
        """
        rec = self._videos[video_id]
        rec.status = "analyzing"
        rec.stage = "detecting"
        duration = duration_hint_s or 360.0
        rec.analysis = analyze_synthetic(video_id, duration, rules=self._rules)
        rec.duration_s = duration
        rec.pipeline_version = rec.analysis.pipeline_version
        rec.progress_pct = 100
        rec.stage = "ready"
        rec.status = "ready"
        return rec

    # --- renders --------------------------------------------------------
    def create_render(self, video_id: str, config: dict) -> RenderRecord:
        rec = RenderRecord(id=_id("rnd"), video_id=video_id, config=config, state="queued")
        with self._lock:
            self._renders[rec.id] = rec
        # Stand-in: mark ready immediately with a fake signed URL.
        rec.state = "ready"
        rec.download_url = f"https://cdn.example/renders/{rec.id}.mp4?sig=stub"
        rec.size_bytes = 0
        return rec

    def get_render(self, render_id: str) -> RenderRecord | None:
        return self._renders.get(render_id)


def filter_config_from_payload(payload: dict, rules: Rules) -> FilterConfig:
    """Build a :class:`FilterConfig` from an API config payload."""
    preset = payload.get("preset", "L2")
    if preset != "custom":
        return FilterConfig.from_preset(preset, rules)
    cfg = FilterConfig.from_preset("custom", rules)
    for cid, setting in payload.get("categories", {}).items():
        if setting.get("enabled"):
            cfg.cut.add(cid)
        cfg.sensitivity[cid] = setting.get("sensitivity", "medium")
    return cfg


store = Store()
