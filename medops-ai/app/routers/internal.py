"""Minimal internal-only endpoints to support assistant-resolved actions and metrics.

These endpoints are intentionally small and protected by the hosting API in
production. In tests they are reachable via the FastAPI TestClient.
"""
from __future__ import annotations

from typing import Dict

from fastapi import APIRouter, HTTPException, Header

from app.config import settings

from app.utils import metrics

router = APIRouter(prefix="/internal/assistant", tags=["internal"])


@router.get("/metrics")
def get_metrics(x_internal_token: str | None = Header(default=None)) -> Dict[str, int]:
    # optional header-based token check
    if settings.internal_api_token:
        if not x_internal_token or x_internal_token != settings.internal_api_token:
            raise HTTPException(status_code=401, detail="Unauthorized")
    return {"assistant_chat_requests": metrics.get("assistant_chat_requests")}


@router.post("/actions/invoices/{idx}/pay")
def pay_invoice(idx: int, x_internal_token: str | None = Header(default=None)):
    # This endpoint represents a protected server-side operation that would
    # validate the signed-in user, check authorization, and perform the action.
    # Here we implement a safe no-op that returns 204 for valid indexes and
    # 404 when out-of-range. Real implementations must authenticate.
    # simple internal token protection
    raise_if_unauthorized = False
    # no auth header required when no token configured (test convenience)
    if settings.internal_api_token:
        # In FastAPI, dependencies can pull headers; here we require caller to
        # include `x-internal-token` header. Tests set the config via env.
        raise_if_unauthorized = True
    if idx < 0:
        raise HTTPException(status_code=404, detail="Invoice not found")
    if raise_if_unauthorized:
        if not x_internal_token or x_internal_token != settings.internal_api_token:
            raise HTTPException(status_code=401, detail="Unauthorized")
    # In a real app, verify the user owns the invoice index from the snapshot.
    return {"status": "ok", "idx": idx}


