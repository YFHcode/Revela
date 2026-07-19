"""Golden-set loader/validator tests (SPEC §12)."""
from __future__ import annotations

from pathlib import Path

import pytest

from eval import golden

TEMPLATE = Path(__file__).parent.parent / "golden" / "TEMPLATE.json"
FIXTURE = Path(__file__).parent / "fixtures" / "golden_mini.json"


def test_template_is_valid():
    clips = golden.load(TEMPLATE)
    assert len(clips) == 2


def test_fixture_is_valid_and_has_stats():
    clips = golden.load(FIXTURE)
    st = golden.stats(clips)
    assert st.clips == 3
    assert st.clean_clips >= 1          # includes the clean hard-negative clip
    assert "C1" in st.by_category


def test_rejects_bad_category():
    with pytest.raises(golden.GoldenValidationError):
        golden.validate([{
            "video_id": "x", "duration_s": 10.0, "shots": [],
            "truth": [{"category": "C9", "start_s": 1.0, "end_s": 2.0}],
        }])


def test_rejects_truth_outside_duration():
    with pytest.raises(golden.GoldenValidationError):
        golden.validate([{
            "video_id": "x", "duration_s": 10.0, "shots": [],
            "truth": [{"category": "C1", "start_s": 5.0, "end_s": 20.0}],
        }])


def test_rejects_overlapping_shots():
    with pytest.raises(golden.GoldenValidationError):
        golden.validate([{
            "video_id": "x", "duration_s": 10.0,
            "shots": [
                {"idx": 0, "start_s": 0.0, "end_s": 6.0},
                {"idx": 1, "start_s": 4.0, "end_s": 10.0},
            ],
            "truth": [],
        }])


def test_rejects_duplicate_video_id():
    with pytest.raises(golden.GoldenValidationError):
        golden.validate([
            {"video_id": "dup", "duration_s": 5.0, "shots": [], "truth": []},
            {"video_id": "dup", "duration_s": 5.0, "shots": [], "truth": []},
        ])


def test_detection_shot_ref_must_exist():
    with pytest.raises(golden.GoldenValidationError):
        golden.validate([{
            "video_id": "x", "duration_s": 10.0,
            "shots": [{"idx": 0, "start_s": 0.0, "end_s": 10.0}],
            "detections": [{"shot_idx": 5, "category": "C1", "score": 0.9}],
            "truth": [],
        }])
