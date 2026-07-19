"""API request/response models (SPEC §9). Pydantic v2."""
from __future__ import annotations

from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field

from analyzer.schema import CATEGORIES


class VideoStatus(str, Enum):
    uploading = "uploading"
    scanning = "scanning"
    analyzing = "analyzing"
    ready = "ready"
    failed = "failed"


class Preset(str, Enum):
    L1 = "L1"
    L2 = "L2"
    L3 = "L3"
    custom = "custom"


class Sensitivity(str, Enum):
    low = "low"
    medium = "medium"
    high = "high"


# --- create / upload ---------------------------------------------------------

class VideoCreate(BaseModel):
    title: str = Field(..., max_length=300)
    size_bytes: int = Field(..., gt=0)
    duration_hint_s: float | None = None


class UploadTarget(BaseModel):
    video_id: str
    upload_url: str
    method: Literal["PUT", "POST"] = "PUT"
    # In production this is a tus endpoint or an S3 multipart target (SPEC FR-1).


class VideoOut(BaseModel):
    id: str
    title: str
    status: VideoStatus
    duration_s: float | None = None
    progress_pct: int = 0
    stage: str | None = None
    pipeline_version: str | None = None


# --- segments / config -------------------------------------------------------

class SegmentOut(BaseModel):
    id: str | None = None
    category: str
    start_s: float
    end_s: float
    score: float
    origin: str = "machine"


class SegmentsOut(BaseModel):
    video_id: str
    config: FilterConfigIn
    segments: list[SegmentOut]
    removed_s: float
    duration_s: float


class CategorySetting(BaseModel):
    enabled: bool = False
    sensitivity: Sensitivity = Sensitivity.medium


class FilterConfigIn(BaseModel):
    preset: Preset = Preset.L2
    # Only used when preset == custom; maps category id -> setting.
    categories: dict[str, CategorySetting] = Field(default_factory=dict)

    def category_ids(self) -> list[str]:
        return list(CATEGORIES)


class SegmentOverride(BaseModel):
    action: Literal["exclude", "include", "adjust", "manual_add"]
    segment_id: str | None = None
    category: str | None = None
    start_s: float | None = None
    end_s: float | None = None


class OverridesIn(BaseModel):
    overrides: list[SegmentOverride]


# --- playback / render -------------------------------------------------------

class PlaybackOut(BaseModel):
    video_id: str
    hls_master_url: str  # signed, short-lived (SPEC §8.1)
    removed_s: float
    scenes_removed: int


class RenderCreate(BaseModel):
    config: FilterConfigIn


class RenderOut(BaseModel):
    id: str
    video_id: str
    state: Literal["queued", "rendering", "ready", "failed"]
    download_url: str | None = None
    size_bytes: int | None = None


# --- feedback ----------------------------------------------------------------

class FeedbackIn(BaseModel):
    type: Literal["missed", "false_flag"]
    category: str | None = None
    start_s: float
    end_s: float
    note: str | None = None
    training_consent: bool = False  # default OFF (SPEC §11)


SegmentsOut.model_rebuild()
