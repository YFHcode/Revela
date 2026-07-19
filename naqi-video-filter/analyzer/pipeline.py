"""Pipeline orchestration (SPEC §7) — probe → shots → two-pass sampling →
inference → aggregation → rules → ``analysis.json``.

Two entry points:

* :func:`analyze` — real media through ffprobe + a registered scorer. Needs
  FFmpeg; the frame decoding for real models is delegated to the scorer.
* :func:`analyze_synthetic` — no media required: fabricate a timeline of shots
  and score them with the mock scorer. Lets you exercise the whole rules /
  delivery / API stack anywhere.

Both return an :class:`AnalysisResult`. Levels are applied *after* analysis, so
the result stores default-level machine segments plus everything needed to
resolve any other level (SPEC §4.2).
"""
from __future__ import annotations

from pathlib import Path

from . import aggregate, sampling
from . import shots as shots_mod
from .config import FilterConfig, Rules
from .models import get_scorer
from .models.base import FrameScorer
from .probe import probe
from .rules import resolve_segments
from .schema import AnalysisResult, Shot


def _run_inference(
    shots: list[Shot],
    scorer: FrameScorer,
    frame_loader,
):
    """Two-pass sampling + inference → detections. ``frame_loader(ts)`` returns
    the pixels for a timestamp (or ``None`` in mock mode)."""
    # Pass 1 — uniform 1 fps, >= 3 frames/shot.
    p1 = sampling.pass1_timestamps(shots)
    frames = [(t, frame_loader(t)) for ts in p1.values() for t in ts]
    frame_scores = scorer.score_frames(frames)
    detections = aggregate.aggregate_shot_scores(
        shots, frame_scores, scorer.name, scorer.version, sampling.PASS1_FPS
    )

    # Pass 2 — re-sample borderline shots at 4 fps and replace their detections.
    shots_by_idx = {s.idx: s for s in shots}
    borderline = sampling.borderline_shots(aggregate.max_score_per_shot(detections))
    if borderline:
        p2 = sampling.pass2_timestamps(shots_by_idx, borderline)
        frames2 = [(t, frame_loader(t)) for ts in p2.values() for t in ts]
        fs2 = scorer.score_frames(frames2)
        dets2 = aggregate.aggregate_shot_scores(
            [shots_by_idx[i] for i in borderline],
            fs2, scorer.name, scorer.version, sampling.PASS2_FPS,
        )
        keep = set(borderline)
        detections = [d for d in detections if d.shot_idx not in keep] + dets2
    return detections


def analyze(
    path: str | Path,
    rules: Rules | None = None,
    model: str = "mock",
    default_config: FilterConfig | None = None,
) -> AnalysisResult:
    """Analyze a real media file end-to-end."""
    rules = rules or Rules.load()
    info = probe(path)
    shots = shots_mod.detect_shots(path, info.fps, info.duration_s)
    scorer = get_scorer(model)

    # Real frame decoding is the scorer's concern; mock ignores pixels.
    def frame_loader(_ts: float):
        return None

    detections = _run_inference(shots, scorer, frame_loader)
    result = AnalysisResult(
        video_id=Path(path).stem,
        duration_s=info.duration_s,
        shots=shots,
        detections=detections,
    )
    _attach_default_segments(result, rules, default_config)
    return result


def analyze_synthetic(
    video_id: str,
    duration_s: float,
    scenes: list[tuple[float, float, str, float]] | None = None,
    rules: Rules | None = None,
    default_config: FilterConfig | None = None,
) -> AnalysisResult:
    """Run the pipeline with fabricated shots + the mock scorer (no media)."""
    rules = rules or Rules.load()
    shots = shots_mod.uniform_shots(duration_s)
    from .models.mock import MockFrameScorer

    scorer = MockFrameScorer(scenes=scenes)
    detections = _run_inference(shots, scorer, lambda _ts: None)
    result = AnalysisResult(
        video_id=video_id, duration_s=duration_s, shots=shots, detections=detections
    )
    _attach_default_segments(result, rules, default_config)
    return result


def _attach_default_segments(
    result: AnalysisResult, rules: Rules, config: FilterConfig | None
) -> None:
    config = config or FilterConfig.from_preset("L2", rules)
    matrix = aggregate.scores_matrix(result.detections)
    result.segments = resolve_segments(result.shots, matrix, config, rules)
