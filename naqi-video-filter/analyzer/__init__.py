"""Naqi detection pipeline — standalone, CLI-runnable analysis package (SPEC §7).

analyze once → store timestamped detections → resolve any strictness level from
the same analysis without re-scanning.
"""
from .config import FilterConfig, Rules
from .pipeline import analyze, analyze_synthetic
from .rules import cut_ranges, resolve_segments
from .schema import (
    CATEGORIES,
    PIPELINE_VERSION,
    AnalysisResult,
    Category,
    Detection,
    Segment,
    Shot,
)

__all__ = [
    "CATEGORIES",
    "PIPELINE_VERSION",
    "AnalysisResult",
    "Category",
    "Detection",
    "FilterConfig",
    "Rules",
    "Segment",
    "Shot",
    "analyze",
    "analyze_synthetic",
    "cut_ranges",
    "resolve_segments",
]
