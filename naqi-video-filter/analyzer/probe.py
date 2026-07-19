"""Stage A — probe & normalize (SPEC §7.1).

Wraps ``ffprobe`` to read container metadata and reject unsupported/corrupt
files. Mezzanine transcoding (the second half of Stage A) is a worker concern
and lives in the render/packaging layer; here we only need duration/fps/codecs
to drive sampling.
"""
from __future__ import annotations

import json
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path

SUPPORTED_CONTAINERS = {
    "mp4", "mov", "mkv", "avi", "webm", "matroska,webm", "mov,mp4,m4a,3gp,3g2,mj2",
}


class ProbeError(RuntimeError):
    pass


@dataclass(frozen=True)
class MediaInfo:
    duration_s: float
    width: int
    height: int
    fps: float
    vcodec: str
    acodec: str | None
    container: str


def ffprobe_available() -> bool:
    return shutil.which("ffprobe") is not None


def _parse_fps(rate: str) -> float:
    if not rate or rate == "0/0":
        return 0.0
    if "/" in rate:
        num, den = rate.split("/")
        den = float(den)
        return float(num) / den if den else 0.0
    return float(rate)


def probe(path: str | Path) -> MediaInfo:
    """Read media metadata via ffprobe. Raises :class:`ProbeError` on failure."""
    path = Path(path)
    if not path.exists():
        raise ProbeError(f"file not found: {path}")
    if not ffprobe_available():
        raise ProbeError(
            "ffprobe not found on PATH. Install FFmpeg, or run the pipeline in "
            "synthetic mode (analyzer.pipeline.analyze_synthetic / CLI --synthetic)."
        )

    cmd = [
        "ffprobe", "-v", "error", "-print_format", "json",
        "-show_format", "-show_streams", str(path),
    ]
    try:
        raw = subprocess.run(cmd, capture_output=True, text=True, timeout=120, check=True)
    except subprocess.CalledProcessError as e:  # pragma: no cover - needs ffprobe
        raise ProbeError(f"ffprobe failed: {e.stderr.strip()}") from e
    except subprocess.TimeoutExpired as e:  # pragma: no cover
        raise ProbeError("ffprobe timed out") from e

    data = json.loads(raw.stdout)
    streams = data.get("streams", [])
    video = next((s for s in streams if s.get("codec_type") == "video"), None)
    audio = next((s for s in streams if s.get("codec_type") == "audio"), None)
    if video is None:
        raise ProbeError("no video stream found")

    fmt = data.get("format", {})
    duration = float(fmt.get("duration") or video.get("duration") or 0.0)
    if duration <= 0:
        raise ProbeError("could not determine duration")

    return MediaInfo(
        duration_s=duration,
        width=int(video.get("width", 0)),
        height=int(video.get("height", 0)),
        fps=_parse_fps(video.get("avg_frame_rate") or video.get("r_frame_rate", "0/0")),
        vcodec=video.get("codec_name", "unknown"),
        acodec=audio.get("codec_name") if audio else None,
        container=fmt.get("format_name", "unknown"),
    )
