"""Download-render routes (SPEC §8.2, §9)."""
from __future__ import annotations

from fastapi import APIRouter, HTTPException

from ..schemas import RenderCreate, RenderOut
from ..store import store

router = APIRouter(prefix="/api/v1", tags=["renders"])


@router.post("/videos/{video_id}/renders", response_model=RenderOut, status_code=202)
def create_render(video_id: str, body: RenderCreate) -> RenderOut:
    rec = store.get_video(video_id)
    if rec is None:
        raise HTTPException(status_code=404, detail="video not found")
    if rec.analysis is None:
        raise HTTPException(status_code=409, detail="analysis not ready")
    # Real impl enqueues an FFmpeg trim+concat render with audio fades at cuts
    # (SPEC §8.2). The stub store marks it ready with a signed URL.
    render = store.create_render(video_id, body.config.model_dump())
    return RenderOut(
        id=render.id, video_id=video_id, state=render.state,
        download_url=render.download_url, size_bytes=render.size_bytes,
    )


@router.get("/renders/{render_id}", response_model=RenderOut)
def get_render(render_id: str) -> RenderOut:
    render = store.get_render(render_id)
    if render is None:
        raise HTTPException(status_code=404, detail="render not found")
    return RenderOut(
        id=render.id, video_id=render.video_id, state=render.state,
        download_url=render.download_url, size_bytes=render.size_bytes,
    )
