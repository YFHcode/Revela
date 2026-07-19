"""Naqi API service (SPEC §6, §9).

FastAPI app exposing the v1 surface. In v1 this is a single service; uploads
and media bypass it via signed URLs. Auth (JWT / managed) and the PostgreSQL +
Celery backends are wired in M2 — this skeleton runs against an in-memory store
so the surface is demonstrable today.
"""
from __future__ import annotations

from fastapi import FastAPI

from analyzer.schema import PIPELINE_VERSION

from .routers import account, renders, videos

app = FastAPI(
    title="Naqi API",
    version="1.0.0-dev",
    summary="Halal-compliant video filtering — analysis, review, playback, render.",
)

app.include_router(videos.router)
app.include_router(renders.router)
app.include_router(account.router)


@app.get("/health", tags=["meta"])
def health() -> dict:
    return {"status": "ok", "pipeline_version": PIPELINE_VERSION}
