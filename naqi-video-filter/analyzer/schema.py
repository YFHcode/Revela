"""Core data types shared across the detection pipeline.

These dataclasses are the in-memory form of the analysis output contract
described in SPEC §7.6 / Appendix B. They serialize to ``analysis.json`` and
map onto the ``shots`` / ``detections`` / ``segments`` DB tables (SPEC §10).
"""
from __future__ import annotations

import json
from collections.abc import Iterable
from dataclasses import asdict, dataclass, field
from enum import Enum

PIPELINE_VERSION = "1.0.0"

# Category order matters: it is the display order in the review UI and the
# iteration order of the rules engine.
CATEGORIES = ("C1", "C2", "C3", "C4", "C5", "C6")


class Category(str, Enum):
    C1 = "C1"  # explicit sexual activity
    C2 = "C2"  # explicit nudity
    C3 = "C3"  # partial nudity / revealing attire
    C4 = "C4"  # kissing
    C5 = "C5"  # intimate physical contact
    C6 = "C6"  # suggestive / bed scenes


@dataclass(frozen=True)
class Shot:
    """A camera shot. All flagging and cutting snaps to shot boundaries."""

    idx: int
    start_s: float
    end_s: float

    @property
    def duration_s(self) -> float:
        return self.end_s - self.start_s


@dataclass(frozen=True)
class Detection:
    """A single model score for one category on one shot."""

    shot_idx: int
    category: str
    score: float
    model: str
    model_version: str
    sampled_fps: float


@dataclass
class Segment:
    """A contiguous time range flagged for a category. Cut unit for render."""

    category: str
    start_s: float
    end_s: float
    score: float
    origin: str = "machine"  # machine | user
    id: str | None = None

    @property
    def duration_s(self) -> float:
        return self.end_s - self.start_s


@dataclass
class AnalysisResult:
    """Single source of truth per video (SPEC §7.6)."""

    video_id: str
    duration_s: float
    shots: list[Shot] = field(default_factory=list)
    detections: list[Detection] = field(default_factory=list)
    segments: list[Segment] = field(default_factory=list)
    pipeline_version: str = PIPELINE_VERSION

    # ---- serialization -------------------------------------------------
    def to_dict(self) -> dict:
        return {
            "video_id": self.video_id,
            "pipeline_version": self.pipeline_version,
            "duration_s": round(self.duration_s, 3),
            "shots": [asdict(s) for s in self.shots],
            "detections": [asdict(d) for d in self.detections],
            "segments": [
                {k: v for k, v in asdict(s).items() if v is not None}
                for s in self.segments
            ],
        }

    def to_json(self, indent: int | None = 2) -> str:
        return json.dumps(self.to_dict(), indent=indent)

    @classmethod
    def from_dict(cls, data: dict) -> AnalysisResult:
        return cls(
            video_id=data["video_id"],
            duration_s=float(data["duration_s"]),
            pipeline_version=data.get("pipeline_version", PIPELINE_VERSION),
            shots=[Shot(**s) for s in data.get("shots", [])],
            detections=[Detection(**d) for d in data.get("detections", [])],
            segments=[Segment(**s) for s in data.get("segments", [])],
        )


def merge_intervals(
    intervals: Iterable[tuple[float, float]], gap_s: float = 0.0
) -> list[tuple[float, float]]:
    """Union a set of ``(start, end)`` ranges, merging those within ``gap_s``."""
    ordered = sorted((a, b) for a, b in intervals if b > a)
    if not ordered:
        return []
    merged = [list(ordered[0])]
    for start, end in ordered[1:]:
        if start - merged[-1][1] <= gap_s:
            merged[-1][1] = max(merged[-1][1], end)
        else:
            merged.append([start, end])
    return [(a, b) for a, b in merged]
