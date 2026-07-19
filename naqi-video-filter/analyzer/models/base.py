"""Model interface shared by every detector slot (SPEC §7.4).

The pipeline never imports a concrete model directly — it asks the registry
for a :class:`FrameScorer`. This keeps the analysis stages independent of
whichever checkpoint is wired in (NudeNet, SigLIP, X-CLIP, or the mock), and
makes local iteration and evaluation fast, per SPEC §7.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol, runtime_checkable

from ..schema import CATEGORIES


@dataclass(frozen=True)
class FrameScore:
    """Per-category scores for one sampled frame, in ``[0, 1]``."""

    time_s: float
    scores: dict[str, float]

    def __post_init__(self) -> None:
        missing = set(CATEGORIES) - set(self.scores)
        if missing:
            raise ValueError(f"frame score missing categories: {sorted(missing)}")


@runtime_checkable
class FrameScorer(Protocol):
    """Scores a batch of frames sampled from a video.

    Concrete implementations wrap the model slots in SPEC §7.4. Frames are
    passed as ``(time_s, path_or_array)`` pairs; the mock scorer ignores the
    pixels, real ones decode them.
    """

    name: str
    version: str

    def score_frames(self, frames: list[tuple[float, object]]) -> list[FrameScore]:
        ...
