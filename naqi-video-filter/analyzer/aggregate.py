"""Stage E (part 1) — aggregate frame scores to per-shot per-category scores.

Shot score per category = mean of the top-3 frame scores in the shot (SPEC
§7.5): robust to one noisy frame, still sensitive to brief content that only
appears in a couple of frames.
"""
from __future__ import annotations

from .models.base import FrameScore
from .schema import CATEGORIES, Detection, Shot

TOP_K = 3


def aggregate_shot_scores(
    shots: list[Shot],
    frame_scores: list[FrameScore],
    model_name: str,
    model_version: str,
    sampled_fps: float,
) -> list[Detection]:
    """Collapse per-frame scores into one :class:`Detection` per shot/category."""
    # Bucket frames into shots. Shots are contiguous and ordered.
    frames_by_shot: dict[int, list[FrameScore]] = {s.idx: [] for s in shots}
    shot_list = sorted(shots, key=lambda s: s.start_s)
    for fs in frame_scores:
        shot = _locate(fs.time_s, shot_list)
        if shot is not None:
            frames_by_shot[shot.idx].append(fs)

    detections: list[Detection] = []
    for shot in shots:
        frames = frames_by_shot[shot.idx]
        if not frames:
            continue
        for cat in CATEGORIES:
            vals = sorted((f.scores[cat] for f in frames), reverse=True)[:TOP_K]
            score = sum(vals) / len(vals)
            detections.append(
                Detection(
                    shot_idx=shot.idx,
                    category=cat,
                    score=round(score, 4),
                    model=model_name,
                    model_version=model_version,
                    sampled_fps=sampled_fps,
                )
            )
    return detections


def _locate(t: float, ordered_shots: list[Shot]) -> Shot | None:
    """Binary-search the shot containing time ``t`` (end-exclusive)."""
    lo, hi = 0, len(ordered_shots) - 1
    while lo <= hi:
        mid = (lo + hi) // 2
        shot = ordered_shots[mid]
        if t < shot.start_s:
            hi = mid - 1
        elif t >= shot.end_s:
            lo = mid + 1
        else:
            return shot
    # Clamp a timestamp sitting exactly on the final boundary.
    if ordered_shots and t == ordered_shots[-1].end_s:
        return ordered_shots[-1]
    return None


def scores_matrix(detections: list[Detection]) -> dict[int, dict[str, float]]:
    """Reshape detections into ``{shot_idx: {category: score}}``."""
    matrix: dict[int, dict[str, float]] = {}
    for d in detections:
        matrix.setdefault(d.shot_idx, {})[d.category] = d.score
    return matrix


def max_score_per_shot(detections: list[Detection]) -> dict[int, float]:
    """Max category score per shot — drives borderline re-sampling (Stage C)."""
    out: dict[int, float] = {}
    for d in detections:
        out[d.shot_idx] = max(out.get(d.shot_idx, 0.0), d.score)
    return out
