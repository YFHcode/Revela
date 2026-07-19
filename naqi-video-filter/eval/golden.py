"""Golden-set loader & validator (SPEC §12).

The golden dataset is the ground truth that gates M1 and every threshold/model
change thereafter. This module loads it, validates its shape and invariants, and
is runnable in CI:

    python -m eval.golden validate golden/clips.json
    python -m eval.golden stats    golden/clips.json

Entry format is one object per clip (see ``golden/TEMPLATE.json``):

    {
      "video_id": "clip_012",
      "duration_s": 45.0,
      "shots": [{"idx": 0, "start_s": 0.0, "end_s": 4.0}, ...],
      "detections": [{"shot_idx": 0, "category": "C4", "score": 0.8,
                      "model": "...", "model_version": "...", "sampled_fps": 4}, ...],
      "truth": [{"category": "C4", "start_s": 3.0, "end_s": 9.5, "note": "peck"}]
    }

``truth`` is the human label (from the annotation guide). ``detections`` are
optional model output; a freshly-labeled clip may have none until analysed.
"""
from __future__ import annotations

import json
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from analyzer.schema import CATEGORIES

_EPS = 1e-6


class GoldenValidationError(ValueError):
    """Raised with all problems found, one per line."""


@dataclass
class GoldenStats:
    clips: int
    total_duration_s: float
    truth_ranges: int
    by_category: dict[str, int]
    clean_clips: int  # clips with no truth ranges (hard negatives / clean)


def _validate_clip(clip: dict, idx: int) -> list[str]:
    errs: list[str] = []
    where = f"clip[{idx}]"
    vid = clip.get("video_id")
    if not vid:
        errs.append(f"{where}: missing video_id")
    where = f"clip[{idx}] ({vid})" if vid else where

    duration = clip.get("duration_s")
    if not isinstance(duration, (int, float)) or duration <= 0:
        errs.append(f"{where}: duration_s must be a positive number")
        duration = float("inf")

    # shots: contiguous, ordered, non-overlapping, within duration
    shots = clip.get("shots", [])
    prev_end = 0.0
    for i, s in enumerate(shots):
        for key in ("idx", "start_s", "end_s"):
            if key not in s:
                errs.append(f"{where}: shot {i} missing {key}")
        start, end = s.get("start_s", 0.0), s.get("end_s", 0.0)
        if end <= start:
            errs.append(f"{where}: shot {i} has end_s <= start_s ({start}, {end})")
        if start + _EPS < prev_end:
            errs.append(f"{where}: shot {i} overlaps previous (start {start} < {prev_end})")
        if end > duration + _EPS:
            errs.append(f"{where}: shot {i} end_s {end} exceeds duration {duration}")
        prev_end = max(prev_end, end)

    # detections reference valid shots + categories
    shot_idxs = {s.get("idx") for s in shots}
    for i, d in enumerate(clip.get("detections", [])):
        if d.get("category") not in CATEGORIES:
            errs.append(f"{where}: detection {i} bad category {d.get('category')!r}")
        if shots and d.get("shot_idx") not in shot_idxs:
            errs.append(f"{where}: detection {i} shot_idx {d.get('shot_idx')} not in shots")
        score = d.get("score")
        if not isinstance(score, (int, float)) or not (0.0 <= score <= 1.0):
            errs.append(f"{where}: detection {i} score out of [0,1]: {score}")

    # truth ranges: valid category, within duration, start < end
    for i, t in enumerate(clip.get("truth", [])):
        if t.get("category") not in CATEGORIES:
            errs.append(f"{where}: truth {i} bad category {t.get('category')!r}")
        start, end = t.get("start_s"), t.get("end_s")
        if not isinstance(start, (int, float)) or not isinstance(end, (int, float)):
            errs.append(f"{where}: truth {i} start_s/end_s must be numbers")
            continue
        if end <= start:
            errs.append(f"{where}: truth {i} end_s <= start_s ({start}, {end})")
        if start < -_EPS or end > duration + _EPS:
            errs.append(f"{where}: truth {i} range ({start},{end}) outside [0,{duration}]")
    return errs


def load(path: str | Path, validate_data: bool = True) -> list[dict]:
    """Load and (by default) validate a golden set. Raises on invalid data."""
    data = json.loads(Path(path).read_text())
    if not isinstance(data, list):
        raise GoldenValidationError("golden set must be a JSON array of clips")
    if validate_data:
        validate(data)
    return data


def validate(clips: list[dict]) -> None:
    errs: list[str] = []
    seen: set[str] = set()
    for i, clip in enumerate(clips):
        errs.extend(_validate_clip(clip, i))
        vid = clip.get("video_id")
        if vid in seen:
            errs.append(f"clip[{i}]: duplicate video_id {vid!r}")
        seen.add(vid)
    if errs:
        raise GoldenValidationError(
            f"{len(errs)} problem(s) in golden set:\n  " + "\n  ".join(errs)
        )


def stats(clips: list[dict]) -> GoldenStats:
    cats: Counter[str] = Counter()
    clean = 0
    for clip in clips:
        truth = clip.get("truth", [])
        if not truth:
            clean += 1
        for t in truth:
            cats[t["category"]] += 1
    return GoldenStats(
        clips=len(clips),
        total_duration_s=round(sum(c.get("duration_s", 0.0) for c in clips), 1),
        truth_ranges=sum(len(c.get("truth", [])) for c in clips),
        by_category=dict(sorted(cats.items())),
        clean_clips=clean,
    )


def _main(argv: list[str] | None = None) -> int:  # pragma: no cover - CLI glue
    import argparse

    ap = argparse.ArgumentParser(prog="eval.golden", description="Golden-set tools")
    sub = ap.add_subparsers(dest="cmd", required=True)
    v = sub.add_parser("validate", help="validate a golden set")
    v.add_argument("path")
    s = sub.add_parser("stats", help="summarize a golden set")
    s.add_argument("path")
    args = ap.parse_args(argv)

    try:
        clips = load(args.path, validate_data=True)
    except GoldenValidationError as e:
        print(f"INVALID: {e}")
        return 1

    if args.cmd == "validate":
        print(f"OK: {len(clips)} clips valid")
        return 0

    st = stats(clips)
    print(f"clips:            {st.clips}")
    print(f"total duration:   {st.total_duration_s} s")
    print(f"truth ranges:     {st.truth_ranges}")
    print(f"clean/negative:   {st.clean_clips}")
    print(f"by category:      {st.by_category}")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(_main())
