"""Account routes (SPEC §9). Auth is stubbed — v1 uses JWT / managed auth."""
from __future__ import annotations

from fastapi import APIRouter

router = APIRouter(prefix="/api/v1/account", tags=["account"])


@router.get("")
def get_account() -> dict:
    # Real impl returns the authenticated profile + quota (SPEC §9).
    return {
        "id": "usr_demo",
        "email": "demo@example.com",
        "quota_bytes": 50 * 1024**3,
        "used_bytes": 0,
    }


@router.delete("", status_code=202)
def delete_account() -> dict:
    # Hard deletion of all content + derivatives within 24h (SPEC §11).
    return {"status": "account_deletion_scheduled"}
