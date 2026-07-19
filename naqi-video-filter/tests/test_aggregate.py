"""Aggregation & sampling tests."""
from __future__ import annotations

from analyzer import aggregate, sampling
from analyzer.models.base import FrameScore
from analyzer.models.mock import MockFrameScorer
from analyzer.schema import CATEGORIES, Shot


def _zero() -> dict[str, float]:
    return {c: 0.0 for c in CATEGORIES}


def test_shot_score_is_mean_of_top3():
    shot = Shot(idx=0, start_s=0.0, end_s=10.0)
    frames = []
    for t, c4 in [(1, 0.9), (2, 0.8), (3, 0.7), (4, 0.1), (5, 0.0)]:
        s = _zero()
        s["C4"] = c4
        frames.append(FrameScore(time_s=float(t), scores=s))
    dets = aggregate.aggregate_shot_scores([shot], frames, "mock", "0", 1.0)
    c4 = [d for d in dets if d.category == "C4"][0]
    assert abs(c4.score - (0.9 + 0.8 + 0.7) / 3) < 1e-6


def test_frames_are_bucketed_into_correct_shots():
    shots = [Shot(0, 0.0, 5.0), Shot(1, 5.0, 10.0)]
    frames = [
        FrameScore(2.0, {**_zero(), "C1": 0.9}),
        FrameScore(7.0, {**_zero(), "C1": 0.1}),
    ]
    dets = aggregate.aggregate_shot_scores(shots, frames, "mock", "0", 1.0)
    by_shot = {d.shot_idx: d.score for d in dets if d.category == "C1"}
    assert by_shot[0] == 0.9
    assert by_shot[1] == 0.1


def test_pass1_guarantees_min_frames_per_shot():
    shots = [Shot(0, 0.0, 1.0)]  # 1s shot at 1fps would give 1 frame
    ts = sampling.pass1_timestamps(shots)
    assert len(ts[0]) >= sampling.MIN_FRAMES_PER_SHOT


def test_borderline_band_selection():
    scores = {0: 0.2, 1: 0.5, 2: 0.66, 3: 0.35}
    assert set(sampling.borderline_shots(scores)) == {1, 3}


def test_mock_is_deterministic():
    a = MockFrameScorer(seed=1).score_frames([(10.0, None)])
    b = MockFrameScorer(seed=1).score_frames([(10.0, None)])
    assert a[0].scores == b[0].scores
