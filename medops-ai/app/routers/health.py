"""Liveness / readiness endpoint for the MedOps AI service.

Exposes a readiness probe that reports whether the LLM provider and model
are correctly configured.  An unconfigured or invalid provider causes a
non-success (503) response so orchestrators can pull the pod/container
out of rotation.
"""

from __future__ import annotations

from fastapi import APIRouter
from starlette.responses import JSONResponse

from app.config import settings

router = APIRouter(tags=["health"])


@router.get("/health")
async def health():
    """Return service health, including LLM provider readiness.

    Returns HTTP 200 with status ``UP`` when the provider/model resolve
    successfully.  Returns HTTP 503 with status ``DOWN`` when the
    configuration is invalid (unsupported provider or missing model).
    """
    provider = None
    model = None
    misconfigured = False
    try:
        provider = settings.resolved_provider()
        if provider is not None:
            model = settings.active_model
    except ValueError:
        provider = "misconfigured"
        misconfigured = True
    if misconfigured:
        return JSONResponse(
            status_code=503,
            content={
                "status": "DOWN",
                "service": "medops-ai",
                "llm": provider,
                "model": model,
            },
        )
    return {
        "status": "UP",
        "service": "medops-ai",
        "llm": provider or "stub",
        "model": model,
    }
