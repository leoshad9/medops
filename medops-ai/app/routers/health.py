"""Liveness / readiness endpoint."""

from __future__ import annotations

from fastapi import APIRouter

from app.config import settings

router = APIRouter(tags=["health"])


@router.get("/health")
async def health():
    """Return service health and the active LLM configuration state."""
    provider = None
    model = None
    try:
        provider = settings.resolved_provider()
        if provider is not None:
            model = settings.active_model
    except ValueError:
        provider = "misconfigured"
    return {
        "status": "UP",
        "service": "medops-ai",
        "llm": provider or "stub",
        "model": model,
    }
