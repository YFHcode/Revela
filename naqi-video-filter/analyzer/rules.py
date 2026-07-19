"""Stage E (part 2) — the rules engine (SPEC §4.2, §7.5, App. A).

Turns per-shot per-category scores into a cut list for a given
:class:`FilterConfig`. Levels are pure configuration: the same analysis input
produces any strictness level with no re-scanning. Steps:

1. threshold per category (base + sensitivity offset)
2. C6 context boost near confirmed C1
3. flag shots, then hysteresis-extend into lead-in / lead-out
4. build contiguous segments per cut category
5. post-process: pad, merge close segments, drop tiny ones (never C1/C2)

The engine is pure: no I/O, no video — trivially unit-testable, which is why
the whole thing hangs off it.
"""
from __future__ import annotations

from .config import FilterConfig, Rules
from .schema import Segment, Shot


def resolve_segments(
    shots: list[Shot],
    shot_scores: dict[int, dict[str, float]],
    config: FilterConfig,
    rules: Rules,
) -> list[Segment]:
    """Machine cut list for ``config`` over one analysis pass."""
    shots_by_idx = {s.idx: s for s in shots}
    ordered = sorted(shots, key=lambda s: s.start_s)

    boosted = _apply_context_boost(ordered, shot_scores, rules)

    segments: list[Segment] = []
    for category in config.cut:
        if category not in rules.categories:
            continue
        threshold = rules.threshold(category, config.sensitivity_for(category))
        flagged = _flag_with_hysteresis(ordered, boosted, category, threshold, rules)
        segments.extend(_build_segments(ordered, shots_by_idx, boosted, flagged, category))

    duration = ordered[-1].end_s if ordered else 0.0
    segments = _post_process(segments, rules, duration)
    return sorted(segments, key=lambda s: (s.start_s, s.category))


# --- steps -------------------------------------------------------------------

def _apply_context_boost(
    ordered: list[Shot], shot_scores: dict[int, dict[str, float]], rules: Rules
) -> dict[int, dict[str, float]]:
    """Boost a category's score on shots adjacent to a triggering category.

    Implements the C6-near-C1 rule generically: implied-sex framing usually
    sits next to the explicit shot (SPEC §7.5).
    """
    boosted = {idx: dict(scores) for idx, scores in shot_scores.items()}
    for cat, rule in rules.categories.items():
        if not rule.context_boost_near or rule.boost <= 0:
            continue
        for near_cat in rule.context_boost_near:
            near_threshold = rule.base_threshold  # trigger on a confident neighbour
            triggering = {
                s.idx for s in ordered
                if shot_scores.get(s.idx, {}).get(near_cat, 0.0) >= near_threshold
            }
            for pos, shot in enumerate(ordered):
                neighbours = set()
                if pos > 0:
                    neighbours.add(ordered[pos - 1].idx)
                if pos < len(ordered) - 1:
                    neighbours.add(ordered[pos + 1].idx)
                if triggering & neighbours:
                    cur = boosted.setdefault(shot.idx, {}).get(cat, 0.0)
                    boosted[shot.idx][cat] = min(1.0, cur + rule.boost)
    return boosted


def _flag_with_hysteresis(
    ordered: list[Shot],
    scores: dict[int, dict[str, float]],
    category: str,
    threshold: float,
    rules: Rules,
) -> set[int]:
    """Flag shots over ``threshold``, then extend into neighbours while their
    score stays >= ``hysteresis_ratio * threshold`` (captures scene lead-in/out).
    """
    hi = threshold
    lo = rules.post_processing.hysteresis_ratio * threshold

    def sc(idx: int) -> float:
        return scores.get(idx, {}).get(category, 0.0)

    seeds = [i for i, s in enumerate(ordered) if sc(s.idx) >= hi]
    flagged: set[int] = set()
    for pos in seeds:
        flagged.add(ordered[pos].idx)
        # walk backward
        j = pos - 1
        while j >= 0 and sc(ordered[j].idx) >= lo:
            flagged.add(ordered[j].idx)
            j -= 1
        # walk forward
        j = pos + 1
        while j < len(ordered) and sc(ordered[j].idx) >= lo:
            flagged.add(ordered[j].idx)
            j += 1
    return flagged


def _build_segments(
    ordered: list[Shot],
    shots_by_idx: dict[int, Shot],
    scores: dict[int, dict[str, float]],
    flagged: set[int],
    category: str,
) -> list[Segment]:
    """Merge contiguous flagged shots into segments spanning their boundaries."""
    segments: list[Segment] = []
    run: list[Shot] = []

    def flush() -> None:
        if not run:
            return
        peak = max(scores.get(s.idx, {}).get(category, 0.0) for s in run)
        segments.append(
            Segment(
                category=category,
                start_s=run[0].start_s,
                end_s=run[-1].end_s,
                score=round(peak, 4),
                origin="machine",
            )
        )
        run.clear()

    for shot in ordered:
        if shot.idx in flagged:
            run.append(shot)
        else:
            flush()
    flush()
    return segments


def _post_process(segments: list[Segment], rules: Rules, duration: float) -> list[Segment]:
    """Pad, merge close same-category segments, drop tiny ones (never C1/C2)."""
    pp = rules.post_processing

    # pad ±pad_s, clamped to the video
    for seg in segments:
        seg.start_s = round(max(0.0, seg.start_s - pp.pad_s), 3)
        seg.end_s = round(min(duration, seg.end_s + pp.pad_s), 3)

    # merge within-category segments separated by < merge_gap_s
    merged: list[Segment] = []
    by_cat: dict[str, list[Segment]] = {}
    for seg in segments:
        by_cat.setdefault(seg.category, []).append(seg)
    for segs in by_cat.values():
        segs.sort(key=lambda s: s.start_s)
        cur = segs[0]
        for nxt in segs[1:]:
            if nxt.start_s - cur.end_s < pp.merge_gap_s:
                cur.end_s = max(cur.end_s, nxt.end_s)
                cur.score = max(cur.score, nxt.score)
            else:
                merged.append(cur)
                cur = nxt
        merged.append(cur)

    # drop segments shorter than drop_below_s unless the category is never_drop
    out: list[Segment] = []
    for seg in merged:
        never_drop = rules.categories[seg.category].never_drop
        if seg.duration_s < pp.drop_below_s and not never_drop:
            continue
        out.append(seg)
    return out


def cut_ranges(segments: list[Segment], merge_gap_s: float = 0.0) -> list[tuple[float, float]]:
    """Union of all segment ranges — the final timeline removed from playback."""
    from .schema import merge_intervals

    return merge_intervals([(s.start_s, s.end_s) for s in segments], gap_s=merge_gap_s)
