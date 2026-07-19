"""Evaluation harness (SPEC §12).

Runs the rules engine against a golden set of clips/films with ground-truth
segments and reports the release-gating numbers: C1+C2 leakage per hour,
per-category recall, over-cut rate. Designed to run in CI on every threshold /
model / prompt change.

Golden set format (JSON list), one entry per clip::

    {
      "video_id": "clip_012",
      "duration_s": 45.0,
      "shots": [{"idx": 0, "start_s": 0.0, "end_s": 4.0}, ...],
      "detections": [{"shot_idx": 0, "category": "C4", "score": 0.8, ...}, ...],
      "truth": [{"category": "C4", "start_s": 3.0, "end_s": 9.5}, ...]
    }

``detections`` are model output (from a real analysis.json) or synthetic. The
harness resolves segments for the level under test and scores them against
``truth``.
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

from analyzer import aggregate
from analyzer.config import FilterConfig, Rules
from analyzer.rules import resolve_segments
from analyzer.schema import AnalysisResult

from . import metrics

# Release gate for explicit content (SPEC §2.3 / §12).
LEAKAGE_GATE_C1C2_PER_HOUR = 2.0
RECALL_GATE = {"C1": 0.98, "C2": 0.98, "C4": 0.92}


@dataclass
class ClipResult:
    video_id: str
    duration_s: float
    leaked_c1c2_per_hour: float
    over_cut_per_hour: float
    recall_by_cat: dict[str, float] = field(default_factory=dict)


def _truth_ranges(truth: list[dict], cats: set[str]) -> list[tuple[float, float]]:
    return [(t["start_s"], t["end_s"]) for t in truth if t["category"] in cats]


def evaluate_clip(clip: dict, level: str, rules: Rules) -> ClipResult:
    result = AnalysisResult.from_dict(
        {**clip, "pipeline_version": clip.get("pipeline_version", "1.0.0"), "segments": []}
    )
    config = FilterConfig.from_preset(level, rules)
    matrix = aggregate.scores_matrix(result.detections)
    segments = resolve_segments(result.shots, matrix, config, rules)

    pred_ranges = [(s.start_s, s.end_s) for s in segments]
    truth = clip.get("truth", [])

    c1c2 = _truth_ranges(truth, {"C1", "C2"})
    leak = metrics.leakage(c1c2, pred_ranges, result.duration_s)

    recall_by_cat: dict[str, float] = {}
    for cat in sorted({t["category"] for t in truth}):
        cat_truth = _truth_ranges(truth, {cat})
        cat_pred = [(s.start_s, s.end_s) for s in segments if s.category == cat]
        # Recall via time-coverage; falls back to any-category coverage for
        # cut categories that fold into another segment.
        rep = metrics.leakage(cat_truth, pred_ranges if cat in config.cut else cat_pred,
                              result.duration_s)
        recall_by_cat[cat] = rep.recall

    return ClipResult(
        video_id=clip["video_id"],
        duration_s=result.duration_s,
        leaked_c1c2_per_hour=round(leak.leaked_per_hour, 3),
        over_cut_per_hour=metrics.over_cut_seconds_per_hour(
            [(t["start_s"], t["end_s"]) for t in truth], pred_ranges, result.duration_s
        ),
        recall_by_cat=recall_by_cat,
    )


def run(golden_path: str | Path, level: str = "L3", rules_path: str | None = None) -> dict:
    rules = Rules.load(rules_path)
    clips = json.loads(Path(golden_path).read_text())
    results = [evaluate_clip(c, level, rules) for c in clips]

    total_dur = sum(r.duration_s for r in results) or 1.0
    weighted_leak = sum(r.leaked_c1c2_per_hour * r.duration_s for r in results) / total_dur
    recall_agg: dict[str, list[float]] = {}
    for r in results:
        for cat, val in r.recall_by_cat.items():
            recall_agg.setdefault(cat, []).append(val)
    recall_mean = {c: round(sum(v) / len(v), 4) for c, v in recall_agg.items()}

    gates = {
        "leakage_c1c2": weighted_leak <= LEAKAGE_GATE_C1C2_PER_HOUR,
        **{
            f"recall_{c}": recall_mean.get(c, 1.0) >= thr
            for c, thr in RECALL_GATE.items()
        },
    }
    return {
        "level": level,
        "clips": len(results),
        "leakage_c1c2_per_hour": round(weighted_leak, 3),
        "recall": recall_mean,
        "gates": gates,
        "passed": all(gates.values()),
        "per_clip": [r.__dict__ for r in results],
    }


if __name__ == "__main__":  # pragma: no cover
    import argparse
    import sys

    ap = argparse.ArgumentParser(description="Naqi eval harness")
    ap.add_argument("golden", help="path to golden set JSON")
    ap.add_argument("--level", default="L3")
    ap.add_argument("--rules", default=None)
    args = ap.parse_args()
    report = run(args.golden, args.level, args.rules)
    print(json.dumps(report, indent=2))
    sys.exit(0 if report["passed"] else 1)
