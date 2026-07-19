"""Metrics & eval-harness tests (SPEC §12)."""
from __future__ import annotations

import json
from pathlib import Path

from eval import metrics
from eval.harness import LEAKAGE_GATE_C1C2_PER_HOUR, run

FIXTURE = Path(__file__).parent / "fixtures" / "golden_mini.json"


def test_leakage_counts_uncovered_truth():
    truth = [(10.0, 20.0)]
    predicted = [(10.0, 15.0)]  # misses 5s
    rep = metrics.leakage(truth, predicted, duration_s=3600.0)
    assert rep.leaked_s == 5.0
    assert rep.covered_s == 5.0
    assert abs(rep.recall - 0.5) < 1e-9
    assert abs(rep.leaked_per_hour - 5.0) < 1e-9


def test_perfect_cover_zero_leak():
    truth = [(10.0, 20.0)]
    rep = metrics.leakage(truth, [(9.0, 21.0)], duration_s=100.0)
    assert rep.leaked_s == 0.0
    assert rep.recall == 1.0


def test_over_cut_counts_clean_removed():
    truth = [(10.0, 20.0)]
    predicted = [(10.0, 30.0)]  # 10s of clean content removed
    assert metrics.over_cut_seconds_per_hour(truth, predicted, 3600.0) == 10.0


def test_iou():
    assert metrics.segment_iou([(0.0, 10.0)], [(0.0, 10.0)]) == 1.0
    assert metrics.segment_iou([(0.0, 10.0)], [(5.0, 15.0)]) == round(5 / 15, 4)


def test_shot_recall_precision():
    recall, precision = metrics.shot_recall_precision({1, 2, 3}, {1, 2})
    assert recall == round(2 / 3, 4)
    assert precision == 1.0


def test_harness_passes_gate_on_golden_mini():
    report = run(FIXTURE, level="L3")
    # The fixture is constructed so recall-biased detection covers all C1/C2.
    assert report["gates"]["leakage_c1c2"] is True
    assert report["leakage_c1c2_per_hour"] <= LEAKAGE_GATE_C1C2_PER_HOUR
    assert report["clips"] == len(json.loads(FIXTURE.read_text()))
