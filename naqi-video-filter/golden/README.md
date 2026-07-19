# golden/ — Evaluation dataset (SPEC §12)

The ground-truth set that **gates M1 and every threshold/model/prompt change**.
The eval harness (`eval/harness.py`) scores pipeline output against the `truth`
labels here; the leakage/recall gates (SPEC §2.3) decide release.

## ⚠️ Never commit media

Only **labels and timecodes** live here — never the video files. The `source`
field records `Title (year) — timecode` so a labeled range is traceable without
redistributing content (SPEC §11, legal posture). `.gitignore` blocks video
extensions repo-wide.

## Files

| File | Purpose |
|---|---|
| `schema.json` | JSON Schema for a golden set (draft-07). |
| `TEMPLATE.json` | Copy this to start a new set; shows a positive + a hard-negative clip. |
| `clips.json` | The real clip set (~60 clips + ~15 clean — build in M0). *Not yet created.* |
| `films/` | Per-film label sets (4 fully-annotated features). *Not yet created.* |

## Labeling

Use the **[Naqī Annotator](../tools/annotator/)** — a local UI that loads a
video, lets you mark in/out points and pick categories, and exports this exact
schema (video stays on your machine; only labels are exported). Or hand-edit
JSON from `TEMPLATE.json`.

Follow [`../docs/ANNOTATION_GUIDE.md`](../docs/ANNOTATION_GUIDE.md). Two
annotators label independently; the owner adjudicates. Record `guide_version`
and `annotators` on every clip.

## Workflow

```bash
# validate structure + invariants before committing labels
python -m eval.golden validate golden/clips.json

# summarize coverage (per-category counts, clean-clip count)
python -m eval.golden stats golden/clips.json

# run the release gate at a given level
python -m eval.harness golden/clips.json --level L3
```

Target composition (SPEC §12): ~60 clips of 10–60 s covering **every category
including hard negatives** (cheek-kiss greetings, parent-child affection,
beach/medical, dark scenes), ~15 fully-clean clips, and 4 annotated feature
films across genres.

## Detections vs truth

- `truth` — human labels from the annotation guide. Required.
- `detections` — optional model output. Leave `[]` for freshly-labeled clips;
  populate by running the analyzer and pasting its per-shot scores, so the
  harness can measure the real model against the labels.
