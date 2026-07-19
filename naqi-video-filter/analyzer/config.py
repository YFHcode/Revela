"""Loading and modelling of ``rules.yaml`` plus per-user filter config.

The rules file is loaded once and treated as immutable. A ``FilterConfig`` is
the per-video, per-user choice (a preset or a custom per-category setup) that
the rules engine applies to a single analysis pass.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

import yaml

from .schema import CATEGORIES

DEFAULT_RULES_PATH = Path(__file__).resolve().parent.parent / "rules.yaml"

Sensitivity = str  # "low" | "medium" | "high"
Preset = str  # "L1" | "L2" | "L3" | "custom"


@dataclass(frozen=True)
class CategoryRule:
    id: str
    name: str
    base_threshold: float
    never_drop: bool = False
    context_boost_near: tuple[str, ...] = ()
    boost: float = 0.0


@dataclass(frozen=True)
class PostProcessing:
    snap_to_shots: bool = True
    pad_s: float = 0.5
    merge_gap_s: float = 2.0
    hysteresis_ratio: float = 0.6
    drop_below_s: float = 0.5


@dataclass(frozen=True)
class Rules:
    pipeline_version: str
    sensitivity_offsets: dict[str, float]
    categories: dict[str, CategoryRule]
    post_processing: PostProcessing
    levels: dict[str, tuple[str, ...]]

    def threshold(self, category: str, sensitivity: Sensitivity) -> float:
        offset = self.sensitivity_offsets.get(sensitivity, 0.0)
        return self.categories[category].base_threshold + offset

    @classmethod
    def load(cls, path: str | Path | None = None) -> Rules:
        path = Path(path) if path else DEFAULT_RULES_PATH
        raw = yaml.safe_load(path.read_text())
        cats: dict[str, CategoryRule] = {}
        for cid, c in raw["categories"].items():
            cats[cid] = CategoryRule(
                id=cid,
                name=c["name"],
                base_threshold=float(c["base_threshold"]),
                never_drop=bool(c.get("never_drop", False)),
                context_boost_near=tuple(c.get("context_boost_near", ())),
                boost=float(c.get("boost", 0.0)),
            )
        pp = raw.get("post_processing", {})
        return cls(
            pipeline_version=str(raw["pipeline_version"]),
            sensitivity_offsets={k: float(v) for k, v in raw["sensitivity_offsets"].items()},
            categories=cats,
            post_processing=PostProcessing(
                snap_to_shots=bool(pp.get("snap_to_shots", True)),
                pad_s=float(pp.get("pad_s", 0.5)),
                merge_gap_s=float(pp.get("merge_gap_s", 2.0)),
                hysteresis_ratio=float(pp.get("hysteresis_ratio", 0.6)),
                drop_below_s=float(pp.get("drop_below_s", 0.5)),
            ),
            levels={k: tuple(v) for k, v in raw["levels"].items()},
        )


@dataclass
class FilterConfig:
    """Per-video user choice resolved against :class:`Rules`.

    ``cut`` is the set of categories to remove; ``sensitivity`` is per-category
    (Low/Med/High). A preset fills these in; ``custom`` lets the user override.
    """

    preset: Preset = "L2"
    cut: set[str] = field(default_factory=set)
    sensitivity: dict[str, Sensitivity] = field(default_factory=dict)

    @classmethod
    def from_preset(cls, preset: Preset, rules: Rules) -> FilterConfig:
        if preset == "custom":
            return cls(preset="custom", cut=set(), sensitivity={c: "medium" for c in CATEGORIES})
        if preset not in rules.levels:
            raise ValueError(f"unknown preset {preset!r}; expected one of {list(rules.levels)}")
        return cls(
            preset=preset,
            cut=set(rules.levels[preset]),
            sensitivity={c: "medium" for c in CATEGORIES},
        )

    def sensitivity_for(self, category: str) -> Sensitivity:
        return self.sensitivity.get(category, "medium")
