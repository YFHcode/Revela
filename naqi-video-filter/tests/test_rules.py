"""Rules-engine tests — the core logic the whole product hangs off."""
from __future__ import annotations

from analyzer.config import FilterConfig, Rules
from analyzer.rules import cut_ranges, resolve_segments
from analyzer.schema import Shot


def _shots(n: int, length: float = 4.0) -> list[Shot]:
    return [Shot(idx=i, start_s=i * length, end_s=(i + 1) * length) for i in range(n)]


def _flat_scores(n: int, category: str, value: float) -> dict[int, dict[str, float]]:
    return {i: {category: value} for i in range(n)}


def rules() -> Rules:
    return Rules.load()


def test_levels_are_pure_config_over_one_analysis():
    r = rules()
    shots = _shots(10)
    # one strong C4 shot (kissing) in the middle
    scores = {i: {"C4": 0.9 if i == 5 else 0.0} for i in range(10)}

    l1 = resolve_segments(shots, scores, FilterConfig.from_preset("L1", r), r)
    l2 = resolve_segments(shots, scores, FilterConfig.from_preset("L2", r), r)

    # L1 does not cut kissing; L2 does — same analysis input.
    assert all(s.category != "C4" for s in l1)
    assert any(s.category == "C4" for s in l2)


def test_c1_is_recall_biased_and_never_dropped():
    r = rules()
    shots = _shots(6, length=0.3)  # very short shots → tiny segment
    scores = {i: {"C1": 0.6 if i == 2 else 0.0} for i in range(6)}
    segs = resolve_segments(shots, scores, FilterConfig.from_preset("L1", r), r)
    # Even a sub-0.5s C1 hit must survive the drop filter (never_drop).
    assert any(s.category == "C1" for s in segs)


def test_hysteresis_extends_into_lead_in_out():
    r = rules()
    shots = _shots(7)
    # peak in the middle, shoulders above 0.6*threshold (0.6*0.6=0.36)
    scores = {
        0: {"C4": 0.0}, 1: {"C4": 0.40}, 2: {"C4": 0.40}, 3: {"C4": 0.90},
        4: {"C4": 0.40}, 5: {"C4": 0.40}, 6: {"C4": 0.0},
    }
    segs = resolve_segments(shots, scores, FilterConfig.from_preset("L2", r), r)
    c4 = [s for s in segs if s.category == "C4"][0]
    # shoulders (shots 1..5) pulled in by hysteresis, padded ±0.5
    assert c4.start_s <= shots[1].start_s + 0.001
    assert c4.end_s >= shots[5].end_s - 0.001


def test_c6_context_boost_near_c1():
    r = rules()
    shots = _shots(5)
    # C6 alone at 0.55 is below its 0.65 threshold; next to a strong C1 it
    # should be boosted (+0.15) over the line.
    scores = {
        0: {"C1": 0.0, "C6": 0.0},
        1: {"C1": 0.0, "C6": 0.55},
        2: {"C1": 0.95, "C6": 0.0},   # strong C1 neighbour
        3: {"C1": 0.0, "C6": 0.55},
        4: {"C1": 0.0, "C6": 0.0},
    }
    boosted = resolve_segments(shots, scores, FilterConfig.from_preset("L2", r), r)
    assert any(s.category == "C6" for s in boosted)

    # Without the C1 neighbour, the same 0.55 stays below threshold.
    scores_no_c1 = {i: {"C1": 0.0, "C6": v.get("C6", 0.0)} for i, v in scores.items()}
    plain = resolve_segments(shots, scores_no_c1, FilterConfig.from_preset("L2", r), r)
    assert not any(s.category == "C6" for s in plain)


def test_sensitivity_shifts_threshold():
    r = rules()
    shots = _shots(5)
    scores = {i: {"C4": 0.58 if i == 2 else 0.0} for i in range(5)}  # just under 0.60

    med = FilterConfig.from_preset("L2", r)
    assert not any(s.category == "C4" for s in resolve_segments(shots, scores, med, r))

    high = FilterConfig.from_preset("L2", r)
    high.sensitivity["C4"] = "high"  # threshold 0.60 - 0.10 = 0.50
    assert any(s.category == "C4" for s in resolve_segments(shots, scores, high, r))


def test_cut_ranges_unions_overlapping_categories():
    r = rules()
    shots = _shots(6)
    scores = {
        2: {"C1": 0.9, "C6": 0.0},
        3: {"C1": 0.0, "C6": 0.9},  # overlaps after padding
    }
    for i in range(6):
        scores.setdefault(i, {})
    segs = resolve_segments(shots, scores, FilterConfig.from_preset("L2", r), r)
    ranges = cut_ranges(segs)
    # C1 (shot 2) and C6 (shot 3) are adjacent → union collapses to one range.
    assert len(ranges) == 1
