"""Deterministic mock scorer.

Lets the whole pipeline run end-to-end on any machine — no GPU, no model
weights, no network. Scores are a reproducible function of the frame time and
a seed, so a given video always analyses identically. Useful for wiring up the
rules engine, the delivery paths, the API and the eval harness before the real
models (SPEC §7.4) are integrated.

It is NOT a detector. It deliberately plants a few synthetic "scenes" so the
downstream stages have something to flag, merge and cut.
"""
from __future__ import annotations

import hashlib
import math

from ..schema import CATEGORIES
from .base import FrameScore


def _noise(seed: str) -> float:
    """Stable pseudo-random float in [0, 1) from a string seed."""
    h = hashlib.sha256(seed.encode()).hexdigest()
    return int(h[:8], 16) / 0xFFFFFFFF


class MockFrameScorer:
    name = "mock"
    version = "0.1.0"

    def __init__(self, seed: int = 0, scenes: list[tuple[float, float, str, float]] | None = None):
        """``scenes`` is a list of ``(start_s, end_s, category, peak_score)``
        the mock will "detect". If omitted, a small default reel is used so the
        pipeline produces visible output on any input."""
        self.seed = seed
        self.scenes = scenes if scenes is not None else self._default_scenes()

    @staticmethod
    def _default_scenes() -> list[tuple[float, float, str, float]]:
        return [
            (30.0, 42.0, "C4", 0.88),   # a kiss
            (120.0, 155.0, "C1", 0.95),  # explicit scene
            (150.0, 165.0, "C6", 0.72),  # suggestive framing next to C1
            (300.0, 305.0, "C4", 0.61),  # a quick peck (borderline)
        ]

    def score_frames(self, frames: list[tuple[float, object]]) -> list[FrameScore]:
        out: list[FrameScore] = []
        for time_s, _pixels in frames:
            scores = {c: 0.03 + 0.06 * _noise(f"{self.seed}:{c}:{time_s:.2f}") for c in CATEGORIES}
            for start, end, cat, peak in self.scenes:
                if start <= time_s <= end:
                    # Smooth bump peaking mid-scene so boundaries are softer.
                    mid = (start + end) / 2
                    half = max((end - start) / 2, 1e-6)
                    falloff = math.cos(min(abs(time_s - mid) / half, 1.0) * math.pi / 2)
                    scores[cat] = max(scores[cat], round(peak * (0.6 + 0.4 * falloff), 4))
            out.append(FrameScore(time_s=time_s, scores=scores))
        return out
