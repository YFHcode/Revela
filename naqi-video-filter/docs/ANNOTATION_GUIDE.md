# Naqī — Annotation Guide (v1.0)

**Status:** Approved for M0 labeling · **Owner:** Youssef El Fhayel
**Scope:** Visual intimate-content annotation for the golden dataset (SPEC §12).

This guide is the ground-truth contract for the golden dataset. Two annotators
label independently; disagreements are adjudicated by the owner. Labels are
stored in the same `segments` schema the pipeline emits (SPEC §7.6, §10), so the
eval harness scores predictions against them directly.

The governing principle is the product's: **recall over precision**. When unsure
whether a moment belongs to a category, prefer to label it — a labeled scene the
model over-cuts is a UX annoyance; a scene missing from ground truth trains and
measures the model toward leakage, which is the product-killing failure.

---

## 1. What we label

We annotate **time ranges** (start/end in seconds) tagged with a **category
(C1–C6)**. Ranges are per category and may overlap across categories (a bed
scene that is also explicit gets both C6 and C1). Annotate the **full visible
extent** of the content, from the first frame it is recognizable to the last —
the pipeline pads and snaps to shot boundaries later, so annotate what you see,
not where a cut "should" go.

Label schema (one row per range):

```json
{ "category": "C4", "start_s": 128.4, "end_s": 134.0, "note": "prolonged, lit" }
```

Store all ranges for a clip under its `truth` array (see `golden/TEMPLATE.json`).

---

## 2. Category definitions

Each category below gives: the **rule**, **positive examples** (label these),
and **negative examples** (do not label). The negatives are as important as the
positives — they define the hard boundaries the model is measured against.

### C1 — Explicit sexual activity
**Rule:** Any depiction of a sex act, simulated or real, clothed or unclothed.
Includes intercourse, oral sex, manual stimulation, and unmistakable
simulated-sex movement under sheets/clothing.
- **Label:** sex scenes (any state of dress); clearly simulated sex; explicit
  foreplay that reads as a sex act.
- **Do not label as C1:** kissing alone (→ C4); an embrace in bed with no sexual
  act (→ C5/C6); implied sex shown only by before/after framing with no act on
  screen (→ C6).
- **Recall-critical.** If it is plausibly a sex act, label C1. Never omit.

### C2 — Explicit nudity
**Rule:** Exposed genitalia, female breasts (areola/nipple visible), or bare
buttocks.
- **Label:** any frame exposing the above, in any context (including non-sexual:
  nudity in a dramatic or artistic scene still counts for C2).
- **Do not label as C2:** cleavage without areola; side/back nudity with no
  buttocks/genitals visible; a bare male chest (→ C3); implied nudity behind an
  object/blur.
- **Classical-art / painting/sculpture nudity ruling:** **label C2.** v1 detects
  visual nudity regardless of medium; users who want art kept can un-flag it in
  review. Add `note: "artwork"` so we can measure this slice separately.
- **Medical/documentary nudity ruling:** **label C2** (same reasoning). Add
  `note: "medical"` or `note: "documentary"`.
- **Breastfeeding ruling:** **label C2 only if an areola/nipple is visible.** A
  covered or implied breastfeeding scene is **not** labeled. Add
  `note: "breastfeeding"` either way for the hard-negative/edge slice.
- **Recall-critical.** Never omit an exposed frame.

### C3 — Partial nudity / revealing attire
**Rule:** Lingerie, underwear, swimwear, shirtless/bare-chested men, and
highly revealing clothing (deep necklines, very short/sheer garments) where skin
exposure is the salient feature.
- **Label:** bikini/swimwear scenes; lingerie; shirtless men; sheer or minimal
  outfits.
- **Do not label:** ordinary short sleeves, ordinary dresses, everyday fashion
  with modest exposure. C3 is about *revealing* attire, not merely visible skin.
- **Opt-in category.** C3 is precision-biased (SPEC §7.7) and off by default
  outside Level 3. Label it consistently anyway — the data must support strict
  users. When genuinely borderline (revealing or just fashionable?), **do not
  label**; C3 is the one category where we bias away from over-labeling.

### C4 — Kissing
**Rule (CONFIRMED, narrow): lip-to-lip kissing only.**
- **Label:** mouth-to-mouth kisses. Sub-flag duration in `note`:
  `"peck"` (brief, < ~1.5 s) vs `"prolonged"`.
- **Do NOT label — these are hard negatives, not C4:**
  - **Cheek kisses / la bise** — a standard greeting across MENA and Europe.
    Flagging it would drown the product in false positives. Never label as C4.
  - **Forehead / hand / hair kisses** — affectionate, not romantic-oral.
  - **Parent kissing a child** on the cheek/forehead/head.
  - A parent giving a quick lip peck to a small child (familial affection) —
    **do not label**; add `note: "familial"`. When ambiguous whether two adults
    vs parent-child, use context (the surrounding scene) to decide.
- **Hardest visual category** (SPEC §2.3 target: C4 recall ≥ 92%). Quick lip
  pecks between adults are the recall risk — label them, and mark `"peck"` so we
  can measure short-kiss recall separately.

### C5 — Intimate physical contact
**Rule:** Romantic/sexual non-kissing contact: embracing romantically,
caressing, lying together, lap-sitting, cuddling in a clearly romantic frame.
- **Label:** a couple lying entwined; a lingering romantic caress; a romantic
  embrace held with intimate body contact.
- **Do NOT label — hard negatives:**
  - **Parent-child affection** — hugging, holding, a child on a parent's lap,
    carrying a child. Never label. Add `note: "familial"`.
  - **Non-romantic hugs** — greetings, goodbyes (airport hug), congratulations,
    condolences, friends embracing.
  - A brief functional touch (helping someone up, a handshake, a pat).
- Romantic *intent and framing* is the test, not physical proximity.

### C6 — Suggestive / bed scenes
**Rule:** Implied sex and sexually suggestive framing without an explicit act:
before/after-sex framing, undressing/disrobing toward intimacy, seductive dance,
a couple in bed under sheets in a sexual context.
- **Label:** the "morning after" couple in bed; a character undressing in a
  seductive context; a seductive/sensual dance; implied-sex framing adjacent to
  a cut.
- **Do NOT label:** someone sleeping alone; getting dressed in a neutral,
  non-sexual context; changing clothes shown non-sexually; a person in bed sick
  or ill. Context decides — C6 is about *sexual suggestion*, not beds or
  undressing per se.
- **Context note:** the pipeline auto-boosts C6 on shots adjacent to a confirmed
  C1 (SPEC §7.5). Annotators do **not** apply that boost — label C6 only where
  you actually see suggestive content; the model handles adjacency.

---

## 3. Hard negatives (must appear in the dataset)

The golden set must include clean clips that stress each false-positive risk
(SPEC §12). Label these with **no** ranges (or only the correct non-intimate
category) so the eval harness measures over-cut on them:

| Hard negative | Risk it guards against |
|---|---|
| Cheek kissing / la bise greeting | C4 false positives (highest-volume risk) |
| Parent-child hug, kiss, lap-sitting, carrying | C4/C5 false positives |
| Friends/colleagues hugging, handshakes, goodbyes | C5 false positives |
| Beach / pool / swimwear documentary | C3 over-cut |
| Medical / childbirth / anatomy documentary | C2 false positives |
| Breastfeeding (covered) | C2 false positives |
| Classical-art / museum nudity | C2 slice (labeled, measured separately) |
| Dark / low-light non-intimate scenes | detector noise in low light (SPEC §14) |
| Contact sports, dance (non-seductive) | C5/C6 false positives |
| Sleeping alone, getting dressed neutrally | C6 false positives |

Target composition (SPEC §12): ~60 clips of 10–60 s covering every category
**including** the negatives above, ~15 fully-clean clips, and 4 fully-annotated
feature films across genres.

---

## 4. Edge-case ruling quick reference

| Case | Ruling |
|---|---|
| Cheek kiss / la bise | **Not** flagged (hard negative). Post-v1: optional custom toggle only if beta asks. |
| Parent-child affection (hug/kiss/lap) | **Not** flagged (hard negative), `note: "familial"`. |
| Lip peck between adults | **C4**, `note: "peck"`. |
| Forehead / hand kiss | Not flagged. |
| Bare male chest / shirtless | **C3** (not C2). |
| Cleavage, no areola | Not flagged. |
| Breastfeeding, areola visible | **C2**, `note: "breastfeeding"`. |
| Breastfeeding, covered | Not flagged, `note: "breastfeeding"`. |
| Classical-art / sculpture nudity | **C2**, `note: "artwork"`. |
| Medical nudity | **C2**, `note: "medical"`. |
| Implied sex, no act on screen | **C6** (not C1). |
| Undressing, neutral/non-sexual | Not flagged. |
| Seductive dance | **C6**. |

---

## 5. Annotation process

1. **Two annotators** label every clip independently, blind to each other.
2. **Adjudication:** the owner resolves any disagreement (missing range,
   category mismatch, or boundary difference > ~1.0 s). Record the ruling; if it
   reveals a gap in this guide, amend the guide and note the version.
3. **Inter-annotator agreement** is tracked per category as a data-quality
   signal; low agreement on a category flags an ambiguous definition to fix here.
4. **Boundaries:** annotate the true visible extent. Do not pre-snap to shots or
   pre-pad — the pipeline does both.
5. **Versioning:** this guide is versioned; every label set records the guide
   version it was made under, so re-adjudication after a ruling change is
   diffable (mirrors the pipeline's `pipeline_version` discipline).

---

## 6. Validation

Run `python -m eval.golden validate golden/<set>.json` before committing labels.
It checks the schema, that shots are contiguous and non-overlapping, that every
`truth.category` is a valid C1–C6, and that ranges fall within the clip
duration. CI runs the same check (SPEC §12 "runs in CI").

---

*Decisions in this guide implement the resolved SPEC §16 open questions — see
[`DECISIONS.md`](DECISIONS.md).*
