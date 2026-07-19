"""End-to-end API flow: create -> complete (analyze) -> segments at each level
-> playback -> render -> delete."""
from __future__ import annotations

from fastapi.testclient import TestClient

from api.main import app

client = TestClient(app)


def _new_ready_video(duration: float = 360.0) -> str:
    r = client.post("/api/v1/videos", json={"title": "Demo Film", "size_bytes": 1_000_000})
    assert r.status_code == 201
    vid = r.json()["video_id"]
    r = client.post(f"/api/v1/videos/{vid}/complete", params={"duration_hint_s": duration})
    assert r.status_code == 200
    assert r.json()["status"] == "ready"
    return vid


def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_analyze_once_resolve_any_level():
    vid = _new_ready_video()
    removed = {}
    for level in ("L1", "L2", "L3"):
        r = client.get(f"/api/v1/videos/{vid}/segments", params={"config": level})
        assert r.status_code == 200
        removed[level] = r.json()["removed_s"]
    # Stricter levels never remove less than looser ones (same analysis).
    assert removed["L1"] <= removed["L2"] <= removed["L3"]


def test_playback_and_render_flow():
    vid = _new_ready_video()
    r = client.get(f"/api/v1/videos/{vid}/playback", params={"config": "L2"})
    assert r.status_code == 200
    assert r.json()["hls_master_url"].endswith(".m3u8?sig=stub")

    r = client.post(f"/api/v1/videos/{vid}/renders", json={"config": {"preset": "L2"}})
    assert r.status_code == 202
    render = r.json()
    assert render["state"] == "ready"
    r = client.get(f"/api/v1/renders/{render['id']}")
    assert r.status_code == 200


def test_custom_config_cuts_selected_categories():
    vid = _new_ready_video()
    client.put(
        f"/api/v1/videos/{vid}/filter-config",
        json={"preset": "custom", "categories": {"C1": {"enabled": True, "sensitivity": "high"}}},
    )
    r = client.get(f"/api/v1/videos/{vid}/segments", params={"config": "custom"})
    assert r.status_code == 200
    cats = {s["category"] for s in r.json()["segments"]}
    assert cats <= {"C1"}  # only C1 selected


def test_delete_is_hard():
    vid = _new_ready_video()
    assert client.delete(f"/api/v1/videos/{vid}").status_code == 202
    assert client.get(f"/api/v1/videos/{vid}").status_code == 404


def test_feedback_defaults_training_consent_off():
    vid = _new_ready_video()
    r = client.post(
        f"/api/v1/videos/{vid}/feedback",
        json={"type": "missed", "start_s": 100.0, "end_s": 108.0, "category": "C4"},
    )
    assert r.status_code == 202
    assert r.json()["training_consent"] is False
