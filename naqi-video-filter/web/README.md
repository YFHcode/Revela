# web/ — Frontend (planned, M3)

React + Next.js + Tailwind SPA (SPEC §6.1, §5). Not yet scaffolded — the M0–M2
work is API/pipeline first; the UI is built against the API in M3 (SPEC §13).

## Screens (SPEC §5.1, FR-4/5/7)

- **Upload** — resumable upload (tus / S3 multipart), 10 GB / 4 h cap.
- **Library** — user's videos, analysis status, per-video level memory.
- **Analysis progress** — `queued → probing → sampling → detecting → aggregating → ready`.
- **Review timeline** — horizontal timeline, segments colour-coded by category;
  per-segment card with **blurred thumbnail (click-and-hold to reveal, with
  confirmation)**, category, confidence, duration; include/exclude toggle;
  drag to adjust boundaries; "add manual segment" tool. Nothing forces the user
  to view flagged content (SPEC goal §2.2.3).
- **Player** — `hls.js` streaming the filtered playlist; flagged chunks are
  absent from the stream itself (no client-side skipping — SPEC §8.1); "n scenes
  removed" badge; optional seekbar cut markers (toggleable).
- **Download** — request a render, notified when ready.

## i18n / a11y

English, French, Arabic (RTL) from v1 (SPEC §11). WCAG 2.1 AA on core flows.

## Suggested stack

```
npx create-next-app@latest web --ts --tailwind --app
# add: hls.js, @tanstack/react-query, next-intl (i18n), zod
```

Point it at the API with `NEXT_PUBLIC_API_BASE=http://localhost:8000`.
