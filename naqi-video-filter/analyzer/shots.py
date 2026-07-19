"""Stage B — shot boundary detection (SPEC §7.2).

Primary detector is TransNetV2; PySceneDetect's ContentDetector is the simpler
fallback. Both are optional deps — when neither is installed (or in synthetic
mode) we fall back to fixed-length pseudo-shots so the rest of the pipeline
still runs. All flagging and cutting snaps to these boundaries.
"""
from __future__ import annotations

from pathlib import Path

from .schema import Shot


def detect_shots(path: str | Path, fps: float, duration_s: float) -> list[Shot]:
    """Return shots for a real media file, preferring PySceneDetect if present."""
    try:
        return _pyscenedetect(path)
    except ImportError:
        # No detector installed — degrade to uniform pseudo-shots so the
        # pipeline stays runnable. Real deployments install PySceneDetect/TransNetV2.
        return uniform_shots(duration_s)


def _pyscenedetect(path: str | Path) -> list[Shot]:  # pragma: no cover - optional dep
    from scenedetect import ContentDetector, SceneManager, open_video

    video = open_video(str(path))
    manager = SceneManager()
    manager.add_detector(ContentDetector())
    manager.detect_scenes(video)
    scenes = manager.get_scene_list()
    shots: list[Shot] = []
    for idx, (start, end) in enumerate(scenes):
        shots.append(Shot(idx=idx, start_s=start.get_seconds(), end_s=end.get_seconds()))
    return shots or uniform_shots(video.duration.get_seconds())


def uniform_shots(duration_s: float, shot_len_s: float = 4.0) -> list[Shot]:
    """Fixed-length pseudo-shots. Used as fallback and in synthetic mode."""
    shots: list[Shot] = []
    idx = 0
    t = 0.0
    while t < duration_s:
        end = min(t + shot_len_s, duration_s)
        shots.append(Shot(idx=idx, start_s=round(t, 3), end_s=round(end, 3)))
        idx += 1
        t = end
    return shots
