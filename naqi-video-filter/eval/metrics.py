"""Evaluation metrics (SPEC §12).

The headline number is **leakage**: seconds of ground-truth intimate content
NOT covered by machine segments, per viewing hour. For this audience a missed
explicit scene is a product-killing failure, so leakage on C1+C2 is the
release gate (< 2 s/h). Over-cut rate is the UX-cost counterpart.

All functions operate on plain ``(start_s, end_s)`` interval lists so they work
against any ground-truth source, independent of the pipeline internals.
"""
from __future__ import annotations

from dataclasses import dataclass

Interval = tuple[float, float]


def _union(intervals: list[Interval]) -> list[Interval]:
    ordered = sorted((a, b) for a, b in intervals if b > a)
    if not ordered:
        return []
    out = [list(ordered[0])]
    for a, b in ordered[1:]:
        if a <= out[-1][1]:
            out[-1][1] = max(out[-1][1], b)
        else:
            out.append([a, b])
    return [(a, b) for a, b in out]


def _total(intervals: list[Interval]) -> float:
    return sum(b - a for a, b in _union(intervals))


def _intersection(a: list[Interval], b: list[Interval]) -> list[Interval]:
    a, b = _union(a), _union(b)
    out: list[Interval] = []
    i = j = 0
    while i < len(a) and j < len(b):
        lo = max(a[i][0], b[j][0])
        hi = min(a[i][1], b[j][1])
        if hi > lo:
            out.append((lo, hi))
        if a[i][1] < b[j][1]:
            i += 1
        else:
            j += 1
    return out


def _subtract(a: list[Interval], b: list[Interval]) -> list[Interval]:
    """a minus b."""
    a = _union(a)
    b = _union(b)
    out: list[Interval] = []
    for start, end in a:
        cursor = start
        for bs, be in b:
            if be <= cursor or bs >= end:
                continue
            if bs > cursor:
                out.append((cursor, min(bs, end)))
            cursor = max(cursor, be)
            if cursor >= end:
                break
        if cursor < end:
            out.append((cursor, end))
    return [iv for iv in out if iv[1] > iv[0]]


@dataclass
class LeakageReport:
    leaked_s: float
    covered_s: float
    truth_s: float
    duration_s: float

    @property
    def leaked_per_hour(self) -> float:
        hours = self.duration_s / 3600 if self.duration_s else 0.0
        return self.leaked_s / hours if hours else 0.0

    @property
    def recall(self) -> float:
        return self.covered_s / self.truth_s if self.truth_s else 1.0


def leakage(
    truth: list[Interval], predicted: list[Interval], duration_s: float
) -> LeakageReport:
    """Time-based leakage: ground-truth content the prediction failed to cover."""
    truth_total = _total(truth)
    covered = _total(_intersection(truth, predicted))
    leaked = _total(_subtract(truth, predicted))
    return LeakageReport(
        leaked_s=round(leaked, 3),
        covered_s=round(covered, 3),
        truth_s=round(truth_total, 3),
        duration_s=duration_s,
    )


def over_cut_seconds_per_hour(
    truth: list[Interval], predicted: list[Interval], duration_s: float
) -> float:
    """Clean content removed per viewing hour (UX cost)."""
    over = _total(_subtract(predicted, truth))
    hours = duration_s / 3600 if duration_s else 0.0
    return round(over / hours, 3) if hours else 0.0


def segment_iou(truth: list[Interval], predicted: list[Interval]) -> float:
    """Interval IoU of the two range sets (boundary tightness)."""
    inter = _total(_intersection(truth, predicted))
    union = _total(truth + predicted)
    return round(inter / union, 4) if union else 1.0


def shot_recall_precision(
    truth_shots: set[int], predicted_shots: set[int]
) -> tuple[float, float]:
    """Shot-level recall & precision (SPEC §12)."""
    if not truth_shots:
        return (1.0, 1.0 if not predicted_shots else 0.0)
    tp = len(truth_shots & predicted_shots)
    recall = tp / len(truth_shots)
    precision = tp / len(predicted_shots) if predicted_shots else 1.0
    return (round(recall, 4), round(precision, 4))
