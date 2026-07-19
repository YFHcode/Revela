"""Stage C — two-pass frame sampling (SPEC §7.3).

Pass 1: uniform 1 fps across the whole video, with a floor of >= 3 frames per
shot (short shots sampled denser). Pass 2: any shot whose max category score
lands in the uncertain band is re-sampled at 4 fps — the specific fix for quick
kisses that a 1 fps pass straddles.

This module only computes *which timestamps to sample*; decoding pixels at
those timestamps is the caller's job (real pipeline) or skipped (mock).
"""
from __future__ import annotations

from .schema import Shot

UNCERTAIN_BAND = (0.35, 0.65)
MIN_FRAMES_PER_SHOT = 3
PASS1_FPS = 1.0
PASS2_FPS = 4.0


def _sample_shot(shot: Shot, fps: float, min_frames: int) -> list[float]:
    """Timestamps for one shot at ``fps``, guaranteeing >= ``min_frames``."""
    n_by_rate = int(shot.duration_s * fps)
    n = max(n_by_rate, min_frames)
    if n <= 1:
        return [round((shot.start_s + shot.end_s) / 2, 3)]
    # Evenly spaced, inset from the hard boundaries to avoid transition frames.
    step = shot.duration_s / n
    return [round(shot.start_s + step * (i + 0.5), 3) for i in range(n)]


def pass1_timestamps(shots: list[Shot]) -> dict[int, list[float]]:
    """Uniform 1 fps sampling, >= 3 frames per shot, keyed by shot idx."""
    return {s.idx: _sample_shot(s, PASS1_FPS, MIN_FRAMES_PER_SHOT) for s in shots}


def borderline_shots(shot_scores: dict[int, float]) -> list[int]:
    """Shot idxs whose max category score is inside the uncertain band."""
    lo, hi = UNCERTAIN_BAND
    return [idx for idx, score in shot_scores.items() if lo <= score <= hi]


def pass2_timestamps(shots_by_idx: dict[int, Shot], idxs: list[int]) -> dict[int, list[float]]:
    """Dense 4 fps re-sampling for the borderline shots."""
    return {i: _sample_shot(shots_by_idx[i], PASS2_FPS, MIN_FRAMES_PER_SHOT + 2) for i in idxs}
