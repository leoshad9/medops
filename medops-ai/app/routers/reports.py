"""Clinical report summarization endpoints."""

from __future__ import annotations

import base64
import logging

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.llm_service import LLMService
from app.services.llm_types import (
    LlmError,
    LlmRateLimitError,
    LlmTimeoutError,
    LlmUnavailableError,
)

router = APIRouter(prefix="/ai", tags=["reports"])

logger = logging.getLogger(__name__)

llm_service = LLMService()


class SummarizeRequest(BaseModel):
    content_base64: str = Field(..., min_length=8, description="PDF bytes as base64")
    content_type: str = Field(default="application/pdf")


class SummarizeResponse(BaseModel):
    report_id: str
    summary: str


@router.post("/reports/{report_id}/summary", response_model=SummarizeResponse)
async def summarize_report(report_id: str, body: SummarizeRequest):
    """Validate a PDF payload and return its generated clinical summary."""
    if body.content_type.lower() != "application/pdf":
        raise HTTPException(status_code=400, detail="Only application/pdf is accepted")

    try:
        pdf_bytes = base64.b64decode(body.content_base64, validate=True)
    except Exception as exc:  # noqa: BLE001 — map any decode failure to 400
        raise HTTPException(status_code=400, detail="Invalid content_base64") from exc

    if not pdf_bytes.startswith(b"%PDF"):
        raise HTTPException(status_code=400, detail="Payload is not a PDF")

    # Do not log PDF bytes or completions.
    logger.info("summarize_report accepted report_id=%s bytes=%s", report_id, len(pdf_bytes))

    try:
        raw = await llm_service.summarize(report_id, pdf_bytes)
        summary = llm_service.validate_summary(raw)
    except ValueError as exc:
        raise HTTPException(status_code=502, detail="Summarizer returned an unusable result") from exc
    except LlmTimeoutError as exc:
        raise HTTPException(status_code=504, detail="LLM request timed out") from exc
    except LlmRateLimitError as exc:
        raise HTTPException(
            status_code=429,
            detail=str(exc) or "LLM provider rate limit exceeded. Please try again shortly.",
        ) from exc
    except LlmUnavailableError as exc:
        raise HTTPException(status_code=503, detail="LLM provider unavailable") from exc
    except LlmError as exc:
        raise HTTPException(status_code=502, detail="LLM summarization failed") from exc

    return SummarizeResponse(report_id=report_id, summary=summary)
