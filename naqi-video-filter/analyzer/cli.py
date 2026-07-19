"""Command-line entry point for the analyzer.

    python -m analyzer analyze movie.mp4 --level L2 -o analysis.json
    python -m analyzer analyze --synthetic --duration 360 --video-id demo
    python -m analyzer segments analysis.json --level L3
    python -m analyzer levels

Runs standalone (SPEC §7) so evaluation and local iteration don't need the API
or workers.
"""
from __future__ import annotations

import argparse
import json
import sys

from . import aggregate
from .config import FilterConfig, Rules
from .pipeline import analyze, analyze_synthetic
from .rules import cut_ranges, resolve_segments
from .schema import AnalysisResult


def _fmt_ts(t: float) -> str:
    m, s = divmod(int(t), 60)
    h, m = divmod(m, 60)
    return f"{h:d}:{m:02d}:{s:02d}" if h else f"{m:d}:{s:02d}"


def _print_segments(result: AnalysisResult, segments, config: FilterConfig) -> None:
    ranges = cut_ranges(segments)
    total_cut = sum(b - a for a, b in ranges)
    print(f"\nvideo: {result.video_id}  duration: {_fmt_ts(result.duration_s)}  "
          f"shots: {len(result.shots)}")
    print(f"preset: {config.preset}  cut categories: {sorted(config.cut) or '—'}")
    print(f"segments: {len(segments)}   removed: {_fmt_ts(total_cut)} "
          f"({100 * total_cut / result.duration_s:.1f}% of runtime)\n")
    for seg in segments:
        print(f"  [{seg.category}] {_fmt_ts(seg.start_s)}–{_fmt_ts(seg.end_s)} "
              f"({seg.duration_s:5.1f}s)  score={seg.score:.2f}")


def cmd_analyze(args: argparse.Namespace) -> int:
    rules = Rules.load(args.rules)
    config = FilterConfig.from_preset(args.level, rules)
    if args.synthetic:
        result = analyze_synthetic(args.video_id, args.duration, rules=rules, default_config=config)
    else:
        if not args.input:
            print("error: provide a media file, or use --synthetic", file=sys.stderr)
            return 2
        result = analyze(args.input, rules=rules, model=args.model, default_config=config)

    if args.output:
        with open(args.output, "w") as fh:
            fh.write(result.to_json())
        print(f"wrote {args.output}")
    _print_segments(result, result.segments, config)
    return 0


def cmd_segments(args: argparse.Namespace) -> int:
    rules = Rules.load(args.rules)
    with open(args.analysis) as fh:
        result = AnalysisResult.from_dict(json.load(fh))
    config = FilterConfig.from_preset(args.level, rules)
    matrix = aggregate.scores_matrix(result.detections)
    segments = resolve_segments(result.shots, matrix, config, rules)
    _print_segments(result, segments, config)
    return 0


def cmd_levels(args: argparse.Namespace) -> int:
    rules = Rules.load(args.rules)
    print("Strictness presets (SPEC §4.2):")
    for name, cats in rules.levels.items():
        labels = ", ".join(f"{c}:{rules.categories[c].name}" for c in cats)
        print(f"  {name}: cut [{labels}]")
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="analyzer", description="Naqi detection pipeline")
    p.add_argument("--rules", default=None, help="path to rules.yaml (default: repo root)")
    sub = p.add_subparsers(dest="command", required=True)

    a = sub.add_parser("analyze", help="analyze a video (or --synthetic)")
    a.add_argument("input", nargs="?", help="media file")
    a.add_argument("--synthetic", action="store_true", help="no media; fabricate a timeline")
    a.add_argument("--duration", type=float, default=360.0, help="synthetic duration (s)")
    a.add_argument("--video-id", default="demo", help="synthetic video id")
    a.add_argument("--model", default="mock", help="scorer name (default: mock)")
    a.add_argument("--level", default="L2", help="preset L1/L2/L3 (default: L2)")
    a.add_argument("-o", "--output", help="write analysis.json here")
    a.set_defaults(func=cmd_analyze)

    s = sub.add_parser("segments", help="resolve a level from an existing analysis.json")
    s.add_argument("analysis", help="path to analysis.json")
    s.add_argument("--level", default="L2", help="preset L1/L2/L3")
    s.set_defaults(func=cmd_segments)

    lv = sub.add_parser("levels", help="show preset definitions")
    lv.set_defaults(func=cmd_levels)
    return p


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
