"""Video lifecycle routes (SPEC §9): create, complete, library, segments,
config, playback, feedback, delete."""
from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query

from analyzer import aggregate, cut_ranges, resolve_segments

from ..schemas import (
    FeedbackIn,
    FilterConfigIn,
    OverridesIn,
    PlaybackOut,
    SegmentOut,
    SegmentsOut,
    UploadTarget,
    VideoCreate,
    VideoOut,
)
from ..store import filter_config_from_payload, store

router = APIRouter(prefix="/api/v1/videos", tags=["videos"])


def _to_video_out(rec) -> VideoOut:
    return VideoOut(
        id=rec.id,
        title=rec.title,
        status=rec.status,
        duration_s=rec.duration_s,
        progress_pct=rec.progress_pct,
        stage=rec.stage,
        pipeline_version=rec.pipeline_version,
    )


def _require(video_id: str):
    rec = store.get_video(video_id)
    if rec is None:
        raise HTTPException(status_code=404, detail="video not found")
    return rec


@router.post("", response_model=UploadTarget, status_code=201)
def create_video(body: VideoCreate) -> UploadTarget:
    rec = store.create_video(body.title, body.size_bytes)
    # Real impl returns a tus/S3-multipart target; client uploads directly to
    # object storage, bypassing the API (SPEC §6.2, FR-1).
    return UploadTarget(video_id=rec.id, upload_url=f"https://uploads.example/{rec.id}")


@router.post("/{video_id}/complete", response_model=VideoOut)
def complete_upload(video_id: str, duration_hint_s: float | None = None) -> VideoOut:
    rec = _require(video_id)
    # Real impl: enqueue virus scan + analysis (SPEC §6.2). Here it runs inline.
    rec = store.run_analysis(video_id, duration_hint_s)
    return _to_video_out(rec)


@router.get("", response_model=list[VideoOut])
def list_videos() -> list[VideoOut]:
    return [_to_video_out(r) for r in store.list_videos()]


@router.get("/{video_id}", response_model=VideoOut)
def get_video(video_id: str) -> VideoOut:
    return _to_video_out(_require(video_id))


def _resolve(rec, config_payload: dict) -> tuple[list[SegmentOut], float]:
    if rec.analysis is None:
        raise HTTPException(status_code=409, detail="analysis not ready")
    config = filter_config_from_payload(config_payload, store.rules)
    matrix = aggregate.scores_matrix(rec.analysis.detections)
    segments = resolve_segments(rec.analysis.shots, matrix, config, store.rules)
    removed = sum(b - a for a, b in cut_ranges(segments))
    out = [
        SegmentOut(id=s.id, category=s.category, start_s=s.start_s, end_s=s.end_s,
                   score=s.score, origin=s.origin)
        for s in segments
    ]
    return out, removed


@router.get("/{video_id}/segments", response_model=SegmentsOut)
def get_segments(video_id: str, config: str = Query("L2")) -> SegmentsOut:
    rec = _require(video_id)
    payload = {"preset": config, "categories": {}} if config in {"L1", "L2", "L3"} else rec.config
    segments, removed = _resolve(rec, payload)
    return SegmentsOut(
        video_id=video_id,
        config=FilterConfigIn(**payload) if "preset" in payload else FilterConfigIn(),
        segments=segments,
        removed_s=round(removed, 2),
        duration_s=rec.duration_s or 0.0,
    )


@router.patch("/{video_id}/segments", response_model=VideoOut)
def patch_segments(video_id: str, body: OverridesIn) -> VideoOut:
    rec = _require(video_id)
    rec.overrides.extend(o.model_dump() for o in body.overrides)
    return _to_video_out(rec)


@router.put("/{video_id}/filter-config", response_model=VideoOut)
def put_filter_config(video_id: str, body: FilterConfigIn) -> VideoOut:
    rec = _require(video_id)
    rec.config = body.model_dump()
    return _to_video_out(rec)


@router.get("/{video_id}/playback", response_model=PlaybackOut)
def get_playback(video_id: str, config: str = Query("L2")) -> PlaybackOut:
    rec = _require(video_id)
    payload = {"preset": config, "categories": {}} if config in {"L1", "L2", "L3"} else rec.config
    segments, removed = _resolve(rec, payload)
    # Real impl generates a filtered HLS playlist omitting flagged chunks and
    # returns a signed master URL (SPEC §8.1). Stubbed here.
    return PlaybackOut(
        video_id=video_id,
        hls_master_url=f"https://cdn.example/hls/{video_id}/{config}/master.m3u8?sig=stub",
        removed_s=round(removed, 2),
        scenes_removed=len(segments),
    )


@router.post("/{video_id}/feedback", status_code=202)
def post_feedback(video_id: str, body: FeedbackIn) -> dict:
    _require(video_id)
    # Stored with video hash + range + category for model improvement; content
    # is used for training only with explicit opt-in (SPEC FR-8, §11).
    return {"status": "recorded", "training_consent": body.training_consent}


@router.delete("/{video_id}", status_code=202)
def delete_video(video_id: str) -> dict:
    if not store.delete_video(video_id):
        raise HTTPException(status_code=404, detail="video not found")
    # Real impl: async hard-purge of all derivatives within 24h, confirmed to
    # the user (SPEC §11).
    return {"status": "deletion_scheduled", "video_id": video_id}
