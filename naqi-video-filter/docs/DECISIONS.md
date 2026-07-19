# Decision Log

Resolved decisions that shape the build. Each entry records the decision, the
reasoning, and any post-v1 revisit trigger. This is the authority when the SPEC
and reality diverge — the SPEC's §16 "open questions" are closed here.

---

## D1 — C4 (kissing) = lip kissing only
**Decision:** C4 covers **lip-to-lip kissing only**. Cheek kisses and
parent-child affection are **not** flagged and go into the dataset as hard
negatives.
**Why:** Cheek kissing (la bise) is a standard greeting across MENA and Europe;
flagging it would drown the product in false positives and destroy trust in the
opposite direction from leakage.
**Revisit:** Post-v1 only — an optional custom toggle for cheek kissing, and
only if beta users ask for it. Not in v1 scope.
**Affects:** annotation guide §C4, `rules.yaml` C4 semantics, review UI copy.

## D2 — C3 (revealing attire) visible but off by default
**Decision:** C3 is present in the UI, **off by default outside Level 3**
(matches SPEC §7.7 precision-bias and §4.2 level matrix). Not buried behind an
"advanced" disclosure.
**Why:** Strict users are a core segment and must find the category easily;
keeping it opt-in means everyone else avoids over-cutting ordinary attire.
**Affects:** review UI (category visible, unchecked by default outside L3),
annotation guide §C3 (precision-biased labeling).

## D3 — Hosting: EU cloud, self-host-friendly design
**Decision:** Target **EU cloud** for beta; keep the storage layer
S3-compatible (MinIO abstraction) so a GCC or on-prem/self-host deployment later
is a configuration change, not a rewrite.
**Why:** GDPR is the strictest bar among the target markets (Morocco, EU, GCC) —
meet it and the SPEC §11 privacy positioning holds everywhere. The
special-category-adjacent nature of filter data (implies religious belief)
makes the strict bar the right default.
**Beta infra:** cheap EU providers (Scaleway / OVH) for storage+compute; RunPod
for dev GPU work. All behind the same S3 + Postgres + Redis abstractions in
`docker-compose.yml`.
**Affects:** `S3_ENDPOINT`/MinIO config, data-residency posture, `.env.example`,
deployment docs.

---

## Still open (SPEC §16, not blocking M0)

These do not block the golden dataset or M1 and can be decided later:

- **§16.4 Monetization for beta** — free-with-quota → subscription by processing
  hours. Decide before beta onboarding (M4).
- **§16.5 Watermark downloads** — deters redistribution, supports legal posture,
  at a UX cost. Tie to the counsel review of the download feature (SPEC §11).
- **§16.6 Launch jurisdictions for counsel review** — Morocco + which EU/GCC
  markets. Needed before public launch, not before beta.
