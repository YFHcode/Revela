# Model License Audit (M0)

**Status:** Initial findings — verify each checkpoint at build time before M1
integration (SPEC App. C). **Owner:** Youssef El Fhayel · **Date:** July 2026.

The product is a **proprietary, commercial SaaS**. Any model whose license or
weights forbid commercial use, or whose copyleft terms would force us to open
the service, is disqualified for the default pipeline. This audit decides the
M1 detector shortlist.

**Legend:** ✅ cleared for commercial SaaS · ⛔ blocked as-is · ⚠️ conditional /
verify.

---

## Headline finding

**NudeNet (notAI-tech) — the body-part detector the SPEC names for C2/C3 — is
GPL-3.0 / AGPL-3.0. It is a commercial-use blocker as-is** and must not go into
the default proprietary pipeline without separately negotiated written
permission from the author. This was the single highest-risk item (SPEC §14
"Model licenses unfit for commercial use") and it has materialized. A swap plan
is below; C2/C3 must be solved without stock NudeNet weights for v1.

Every other named slot is permissively licensed and cleared, subject to
build-time re-verification of the exact checkpoint.

---

## Per-slot findings (SPEC §7.4)

| Slot | Candidate | License | Verdict | Notes |
|---|---|---|---|---|
| NSFW frame classifier (C1/C2 coarse) | `Falconsai/nsfw_image_detection` (ViT) | Apache-2.0 | ✅ | Whole-frame classifier. Cleared. Confirm the checkpoint card still reads Apache-2.0 at pin time. |
| Body-part detector (C2/C3 fine) | **NudeNet v3 (notAI-tech)** | **GPL-3.0 / AGPL-3.0** | ⛔ | Copyleft; author offers commercial permission by request. Do **not** ship stock weights. See swap plan. |
| Zero-shot frame scorer (C4/C5/C6) | `google/siglip-*` (SigLIP) | Apache-2.0 | ✅ | Primary for the prompt-ensemble categories. Cleared. |
| Zero-shot alt | OpenCLIP (ViT-L) | MIT (code) | ⚠️ | Code MIT; **weights** carry their own terms — LAION-trained checkpoints have separate use notes. Verify the specific checkpoint before use. |
| Shot boundary | **TransNetV2 (soCzech)** | MIT | ✅ | Cleared. Weights + code MIT. |
| Video action model (C4/C1 confirm) | `microsoft/xclip-*` (X-CLIP) | MIT | ⚠️ verify | Microsoft repos are typically MIT; confirm the exact HF checkpoint card. |
| Video action alt | VideoMAE (Kinetics) | ⚠️ often CC-BY-**NC** | ⚠️ | Several VideoMAE/Kinetics checkpoints are **non-commercial**. Prefer X-CLIP; only use a VideoMAE checkpoint confirmed non-NC. |

---

## The C2/C3 gap and swap plan

NudeNet is out, so fine-grained "exposed vs covered, per-part" detection needs
another source. Options, in recommended order:

1. **v1 without a dedicated body-part detector (recommended for beta).**
   Cover C2 with the **Falconsai NSFW classifier (Apache-2.0)** for the coarse
   explicit-nudity signal, and cover C3 with **SigLIP prompt ensembles**
   (Apache-2.0) — "a person in lingerie", "a shirtless man", "a woman in a
   bikini", etc. Validate against the golden set: if C2 recall clears the ≥ 98%
   gate without per-part boxes, we do not need NudeNet for v1 at all. This keeps
   the entire default pipeline Apache/MIT.
2. **Negotiate a commercial license** with the NudeNet author (email / issue, per
   their README). Only worthwhile if (1) misses the C2 gate. Adds a legal
   dependency and cost.
3. **Train an in-house body-part detector** on a permissively-licensed dataset.
   Highest effort; defer unless (1) fails and (2) is unacceptable.

**Action:** M1 evaluates option (1) first against the golden set. NudeNet is not
a dependency until proven necessary.

---

## Checklist to complete per model before M1 exit (SPEC App. C)

For each checkpoint that ships:

- [ ] Exact checkpoint id + revision hash pinned
- [ ] License confirmed from the source card/repo at pin time (not memory)
- [ ] Commercial-use permitted (SaaS, no copyleft obligation on our code)
- [ ] Weights redistribution terms compatible with our container builds
- [ ] Expected VRAM + batched fp16 throughput on target GPU (T4/L4/4090) recorded
- [ ] A swap candidate identified for the slot

---

## Sources

- NudeNet (notAI-tech) — GPL-3.0/AGPL-3.0, commercial use by request: <https://github.com/notAI-tech/NudeNet>
- Falconsai/nsfw_image_detection — Apache-2.0: <https://huggingface.co/Falconsai/nsfw_image_detection>
- SigLIP (google) — Apache-2.0: <https://huggingface.co/google/siglip-so400m-patch14-384>
- TransNetV2 (soCzech) — MIT: <https://github.com/soCzech/TransNetV2>
- X-CLIP (microsoft) — verify exact checkpoint: <https://huggingface.co/microsoft/xclip-base-patch32>

*Findings from public sources, July 2026. Licenses change; re-verify at build
time. This is engineering due diligence, not legal advice — counsel reviews
before public launch (SPEC §11).*
