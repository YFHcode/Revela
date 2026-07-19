# Naqī Annotator

A local, single-file UI for labeling the golden dataset (SPEC §12). It turns the
manual "watch a clip, hand-write JSON" loop into: load video → mark in/out →
pick category → export. Output is the exact `golden/clips.json` schema, and it
passes `python -m eval.golden validate` directly.

## Privacy / legal

**The video never leaves your machine.** It's loaded via the browser's file
picker into an in-memory object URL — nothing is uploaded, and the tool has no
backend. Only the labels (categories + timecodes) are exported. This matches the
project's rule: commit labels, never media (SPEC §11).

## Run it

```bash
cd naqi-video-filter/tools/annotator
python -m http.server 8080        # then open http://localhost:8080
```

(Opening `index.html` directly via `file://` also works in most browsers; a
local server is more reliable for video playback.)

## Workflow

1. **Load video** (button or drag-drop). Duration + a `video_id` slug auto-fill.
2. **Scrub** to the start of an intimate moment. Transport buttons or keys:
   `Space` play/pause, `← →` seek ±1 s (`⇧` = ±5 s), `, .` frame-step.
3. **Mark**: press `I` at the start, `O` at the end (or type seconds directly).
4. **Category**: click C1–C6 or press `1`–`6`. Hover shows the rule; the guide
   in `docs/ANNOTATION_GUIDE.md` is the full contract.
5. **Note** (optional): click a chip (`peck`, `prolonged`, `familial`,
   `artwork`, `medical`, …) or type free text.
6. **Add segment** (`Enter`). It appears in the list and on the timeline strip.
7. Repeat 2–6 for every intimate range. For a clean/hard-negative clip (e.g. a
   cheek-kiss greeting), tick **Clean / hard-negative** instead — exports empty
   `truth`.
8. **Save clip to set**, then load the next video.
9. **Export clips.json** when the batch is done.

## Two annotators

Each annotator exports their own file (`clips.A.json`, `clips.B.json`). The
owner adjudicates disagreements and the reconciled file becomes
`golden/clips.json`. Use **Import set** to reopen and edit an existing file.

## Then

```bash
# from the project root
python -m eval.golden validate golden/clips.json   # structure + invariants
python -m eval.golden stats    golden/clips.json   # coverage by category
```

`shots` and `detections` export empty — they're populated later by the analyzer
(`python -m analyzer analyze`) in M1. `truth` is what the annotator produces and
what the eval harness scores against.
