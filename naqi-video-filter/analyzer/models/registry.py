"""Model registry — resolves a scorer by name.

v1 ships the ``mock`` scorer. Real slots (SPEC §7.4) register here behind
optional imports so the package installs and runs without heavy ML deps; a
missing dependency raises a clear error only when that model is requested.
"""
from __future__ import annotations

from collections.abc import Callable

from .base import FrameScorer
from .mock import MockFrameScorer

_BUILDERS: dict[str, Callable[..., FrameScorer]] = {
    "mock": MockFrameScorer,
}


def register(name: str, builder: Callable[..., FrameScorer]) -> None:
    _BUILDERS[name] = builder


def available() -> list[str]:
    return sorted(_BUILDERS)


def get_scorer(name: str = "mock", **kwargs) -> FrameScorer:
    if name not in _BUILDERS:
        raise ValueError(
            f"unknown model {name!r}. Available: {available()}. "
            "Real detectors (nsfw/nudenet/siglip/xclip) are integrated in M1; "
            "until then use 'mock'."
        )
    return _BUILDERS[name](**kwargs)


# --- Real detector slots (SPEC §7.4) -----------------------------------------
# Each is registered lazily so importing this module never pulls in torch.
# Wire real checkpoints in M1; the license audit (App. C) gates each one.
#
#   register("nsfw",    lambda **kw: _load("analyzer.models.nsfw", "NsfwViT", **kw))
#   register("nudenet", lambda **kw: _load("analyzer.models.nudenet", "NudeNetV3", **kw))
#   register("siglip",  lambda **kw: _load("analyzer.models.siglip", "SigLipScorer", **kw))
#   register("xclip",   lambda **kw: _load("analyzer.models.xclip", "XClipScorer", **kw))
